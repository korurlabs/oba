package com.korur.oba.connect.realtime.interceptor.unit;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.realtime.constant.RealtimeConstant;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.exception.ExceptionDomainRealtimeError;
import com.korur.oba.connect.realtime.helper.RealtimeDestinationAuthorizerRegistry;
import com.korur.oba.connect.realtime.interceptor.RealtimeInboundInterceptor;
import com.korur.oba.connect.realtime.properties.RealtimeProperties;
import com.korur.oba.connect.realtime.service.RealtimeDestinationAuthorizer;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.connect.realtime.util.RealtimeDestinationUtil;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class RealtimeInboundInterceptorUnitTest {

    @Mock
    private MessageChannel channel;
    @Mock
    private RealtimeSessionRegistry realtimeSessionRegistry;

    private LoggingService loggingService;
    private RealtimeProperties realtimeProperties;
    private RealtimeInboundInterceptor interceptor;

    private final UUID userId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        loggingService = mock(LoggingService.class);
        realtimeProperties = new RealtimeProperties();
        RealtimeDestinationAuthorizerRegistry authorizerRegistry = new RealtimeDestinationAuthorizerRegistry(
                List.of(new StubDestinationAuthorizer()), loggingService);
        interceptor = new RealtimeInboundInterceptor(authorizerRegistry, realtimeSessionRegistry, realtimeProperties, loggingService);
        // The session limits are the registry's own; here every session is within them unless a
        // test says otherwise.
        lenient().when(realtimeSessionRegistry.allowsSubscription(anyString())).thenReturn(true);
        lenient().when(realtimeSessionRegistry.tryAcquireSend(anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(true);
    }

    /**
     * Stands in for an app's authorizer: claims one organization topic, the user queue and one
     * send destination, and allows all of them, so the tests exercise the interceptor alone.
     */
    private static final class StubDestinationAuthorizer implements RealtimeDestinationAuthorizer {

        static final String TOPIC_SUFFIX = "/chat";
        static final String USER_QUEUE = "/user/queue/chat";
        static final String SEND_DESTINATION = "/app/chat/typing";

        @Override
        public String consumer() {
            return "stub";
        }

        @Override
        public boolean supportsSubscribe(String destination) {
            return USER_QUEUE.equals(destination)
                    || RealtimeDestinationUtil.parseOrganizationTopic(destination, TOPIC_SUFFIX).isPresent();
        }

        @Override
        public boolean authorizeSubscribe(RealtimePrincipal principal, String destination) {
            return true;
        }

        @Override
        public boolean supportsSend(String destination) {
            return SEND_DESTINATION.equals(destination);
        }

        @Override
        public boolean authorizeSend(RealtimePrincipal principal, String destination) {
            return true;
        }
    }

    private static String chatTopic(UUID organization) {
        return RealtimeDestinationUtil.organizationTopic(organization, StubDestinationAuthorizer.TOPIC_SUFFIX);
    }

    private Message<byte[]> connectFrame(Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-1");
        accessor.setSessionAttributes(sessionAttributes);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Map<String, Object> handshakeAttributes(RealtimePrincipal principal) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(RealtimeConstant.SESSION_PRINCIPAL_ATTRIBUTE, principal);
        return attributes;
    }

    private Message<byte[]> frame(StompCommand command, String destination, RealtimePrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        accessor.setDestination(destination);
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeFrame(String destination, String subscriptionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-1");
        accessor.setDestination(destination);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setUser(livePrincipal());
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private RealtimePrincipal livePrincipal() {
        return new RealtimePrincipal(userId, organizationId, Instant.now().plusSeconds(300));
    }

    private void assertRejectedWith(Message<?> message, ExceptionDomainRealtimeError error) {
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining(error.getCode());
    }

    @Nested
    class Connect {

        @Test
        void handshakePrincipalPresent_acceptsSetsPrincipalAndTakesItFromTheSession() {
            RealtimePrincipal principal = livePrincipal();
            Map<String, Object> attributes = handshakeAttributes(principal);
            Message<byte[]> message = connectFrame(attributes);

            Message<?> result = interceptor.preSend(message, channel);

            assertThat(result).isSameAs(message);
            assertThat(StompHeaderAccessor.wrap(result).getUser()).isEqualTo(principal);
            assertThat(attributes).doesNotContainKey(RealtimeConstant.SESSION_PRINCIPAL_ATTRIBUTE);
        }

        @Test
        void noHandshakePrincipal_rejectsWithUnauthenticated() {
            assertRejectedWith(connectFrame(new HashMap<>()), ExceptionDomainRealtimeError.SEC_REALTIME_UNAUTHENTICATED);
        }

        @Test
        void noSessionAttributes_rejectsWithUnauthenticated() {
            assertRejectedWith(connectFrame(null), ExceptionDomainRealtimeError.SEC_REALTIME_UNAUTHENTICATED);
        }

        @Test
        void secondConnectOnTheSameSocket_rejectsWithUnauthenticated() {
            Map<String, Object> attributes = handshakeAttributes(livePrincipal());
            interceptor.preSend(connectFrame(attributes), channel);

            assertRejectedWith(connectFrame(attributes), ExceptionDomainRealtimeError.SEC_REALTIME_UNAUTHENTICATED);
            verify(loggingService).warn(any(), eq(LogLayer.HANDLER), eq(RealtimeConstant.CONNECT_REJECTED),
                    eq(ExceptionDomainRealtimeError.SEC_REALTIME_UNAUTHENTICATED.getCode()),
                    eq(RealtimeConstant.REASON_NO_HANDSHAKE_PRINCIPAL), eq("session-1"));
        }

        @Test
        void tokenExpiredBetweenTicketAndConnect_rejectsWithUnauthenticated() {
            RealtimePrincipal expired = new RealtimePrincipal(userId, organizationId, Instant.now().minusSeconds(1));

            assertRejectedWith(connectFrame(handshakeAttributes(expired)),
                    ExceptionDomainRealtimeError.SEC_REALTIME_UNAUTHENTICATED);
        }
    }

    @Nested
    class Subscribe {

        @Test
        void ownOrganizationTopic_isAllowed() {
            Message<byte[]> message = frame(StompCommand.SUBSCRIBE,
                    chatTopic(organizationId), livePrincipal());

            assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        }

        @Test
        void ownUserQueue_isAllowed() {
            Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/user/queue/chat", livePrincipal());

            assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        }

        @Test
        void otherOrganizationTopic_isRefused() {
            assertRejectedWith(frame(StompCommand.SUBSCRIBE,
                            chatTopic(UUID.randomUUID()), livePrincipal()),
                    ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN);
        }

        @Test
        void otherUsersQueue_isRefused() {
            assertRejectedWith(frame(StompCommand.SUBSCRIBE,
                            "/user/" + UUID.randomUUID() + "/queue/chat", livePrincipal()),
                    ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN);
        }

        @Test
        void expiredSession_isRefusedAsUnauthenticated() {
            RealtimePrincipal expired = new RealtimePrincipal(userId, organizationId, Instant.now().minusSeconds(1));

            assertRejectedWith(frame(StompCommand.SUBSCRIBE,
                            chatTopic(organizationId), expired),
                    ExceptionDomainRealtimeError.SEC_REALTIME_UNAUTHENTICATED);
        }

        @Test
        void sessionWithoutPrincipal_isRefusedAsUnauthenticated() {
            assertRejectedWith(frame(StompCommand.SUBSCRIBE,
                            chatTopic(organizationId), null),
                    ExceptionDomainRealtimeError.SEC_REALTIME_UNAUTHENTICATED);
        }

        @Test
        void wildcardPattern_isRefused() {
            assertRejectedWith(frame(StompCommand.SUBSCRIBE, "/topic/organizations/*/chat", livePrincipal()),
                    ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN);
        }

        @Test
        void rawQueue_isRefused() {
            assertRejectedWith(frame(StompCommand.SUBSCRIBE, "/queue/chat", livePrincipal()),
                    ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN);
        }

        @Test
        void selectorHeader_isRefused() {
            Message<byte[]> message = frame(StompCommand.SUBSCRIBE, chatTopic(organizationId), livePrincipal());
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            accessor.addNativeHeader(RealtimeConstant.SELECTOR_HEADER, "headers.type == 'PRESENCE'");

            assertRejectedWith(MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders()),
                    ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN);
        }

        @Test
        void subscriptionCapReached_isDroppedAndTheSessionStays() {
            when(realtimeSessionRegistry.allowsSubscription("session-1")).thenReturn(false);
            Message<byte[]> message = frame(StompCommand.SUBSCRIBE, chatTopic(organizationId), livePrincipal());

            assertThat(interceptor.preSend(message, channel)).isNull();
        }

        @Test
        void newSubscriptionId_isAllowed() {
            Message<byte[]> message = subscribeFrame(chatTopic(organizationId), "sub-2");

            assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        }

        @Test
        void subscriptionIdTheSessionAlreadyHolds_isRefused() {
            when(realtimeSessionRegistry.holdsSubscription("session-1", "sub-1")).thenReturn(true);

            assertRejectedWith(subscribeFrame("/user/queue/chat", "sub-1"),
                    ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN);
            verify(loggingService).warn(any(), eq(LogLayer.HANDLER), eq(RealtimeConstant.FRAME_REJECTED),
                    eq(ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN.getCode()),
                    eq(RealtimeConstant.REASON_SUBSCRIPTION_ID_REUSED), eq(StompCommand.SUBSCRIBE),
                    eq("/user/queue/chat"), eq("session-1"), eq(livePrincipal().getName()));
        }
    }

    @Nested
    class Send {

        @Test
        void typingDestination_isAllowed() {
            Message<byte[]> message = frame(StompCommand.SEND, "/app/chat/typing", livePrincipal());

            assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        }

        @Test
        void anyOtherDestination_isRefused() {
            assertRejectedWith(frame(StompCommand.SEND, "/app/chat/messages", livePrincipal()),
                    ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN);
        }

        @Test
        void floodPastTheRateLimit_isDroppedAndTheSessionStays() {
            when(realtimeSessionRegistry.tryAcquireSend("session-1", RealtimeConstant.SEND_BUCKET_INBOUND,
                    realtimeProperties.getInboundSendMax(), realtimeProperties.getInboundSendWindowMs()))
                    .thenReturn(false);

            Message<byte[]> excess = frame(StompCommand.SEND, "/app/chat/typing", livePrincipal());

            assertThat(interceptor.preSend(excess, channel)).isNull();
        }

        @Test
        void brokerDestination_isRefused() {
            assertRejectedWith(frame(StompCommand.SEND,
                            chatTopic(organizationId), livePrincipal()),
                    ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN);
        }
    }

    @Nested
    class OtherFrames {

        @Test
        void disconnect_alwaysPasses() {
            Message<byte[]> message = frame(StompCommand.DISCONNECT, null, null);

            assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        }

        @Test
        void disconnectAfterTheTokenExpired_passes() {
            RealtimePrincipal expired = new RealtimePrincipal(userId, organizationId, Instant.now().minusSeconds(1));
            Message<byte[]> message = frame(StompCommand.DISCONNECT, null, expired);

            assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        }

        @Test
        void unsubscribe_passesEvenAfterTheTokenExpired() {
            RealtimePrincipal expired = new RealtimePrincipal(userId, organizationId, Instant.now().minusSeconds(1));
            Message<byte[]> message = frame(StompCommand.UNSUBSCRIBE, null, expired);

            assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        }

        @ParameterizedTest
        @EnumSource(value = StompCommand.class,
                names = {"MESSAGE", "CONNECTED", "RECEIPT", "ERROR", "ACK", "NACK", "BEGIN", "COMMIT", "ABORT"})
        void commandOutsideTheAllowlist_isRefused(StompCommand command) {
            assertRejectedWith(frame(command, chatTopic(organizationId), livePrincipal()),
                    ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN);
            verify(loggingService).warn(any(), eq(LogLayer.HANDLER), eq(RealtimeConstant.FRAME_REJECTED),
                    eq(ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN.getCode()),
                    eq(RealtimeConstant.REASON_COMMAND_NOT_ALLOWED), eq(command),
                    eq(chatTopic(organizationId)), eq("session-1"), eq(livePrincipal().getName()));
        }

        @Test
        void clientMessageFrameToAnotherOrganizationsTopic_isRefusedWithoutASessionUser() {
            assertRejectedWith(frame(StompCommand.MESSAGE, chatTopic(UUID.randomUUID()), null),
                    ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN);
        }

        @Test
        void messageWithoutStompCommand_passes() {
            Message<String> message = MessageBuilder.withPayload("heartbeat").build();

            assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        }
    }
}
