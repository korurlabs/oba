package com.korur.oba.connect.realtime.service;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.DefaultSimpUserRegistry;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// 2. Third-party

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.connect.realtime.constant.RealtimeConstant;
import com.korur.oba.connect.realtime.dto.RealtimeFrameDecision;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.event.RealtimePresenceChangedEvent;
import com.korur.oba.connect.realtime.exception.ExceptionDomainRealtimeError;
import com.korur.oba.connect.realtime.helper.RealtimeDestinationAuthorizerRegistry;
import com.korur.oba.connect.realtime.properties.RealtimeProperties;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.event.AppAccessRevokedEvent;
import com.korur.oba.shrine.organization.event.AccessRevokedEvent;

@ExtendWith(MockitoExtension.class)
class RealtimeSessionRegistryUnitTest {

    private static final String SESSION_ID = "session-1";
    private static final String SECOND_SESSION_ID = "session-2";
    private static final String FIRST_DESTINATION = "/topic/organizations/first/scoped";
    private static final String SECOND_DESTINATION = "/user/queue/chat";
    private static final String BUCKET = "bucket";
    private static final String OTHER_BUCKET = "other-bucket";
    private static final int SEND_MAX = 3;
    private static final long WINDOW_MS = 10_000L;

    @Mock
    private ObjectProvider<MessageChannel> clientOutboundChannel;
    @Mock
    private ObjectProvider<MessageChannel> brokerChannel;
    @Mock
    private RealtimeDestinationAuthorizerRegistry realtimeDestinationAuthorizerRegistry;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private LoggingService loggingService;
    @Mock
    private WebSocketHandler webSocketHandler;
    @Mock
    private WebSocketSession webSocketSession;
    @Mock
    private WebSocketSession secondWebSocketSession;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-22T10:00:00Z"));
    private final RealtimeProperties properties = new RealtimeProperties();
    private RealtimeSessionRegistry registry;
    private final RealtimePrincipal principal =
            new RealtimePrincipal(UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(300));

    @BeforeEach
    void setUp() throws Exception {
        properties.setMaxSubscriptionsPerSession(2);
        registry = new RealtimeSessionRegistry(clientOutboundChannel, brokerChannel,
                realtimeDestinationAuthorizerRegistry, applicationEventPublisher, properties, loggingService, clock);
        when(webSocketSession.getId()).thenReturn(SESSION_ID);
        connect(webSocketSession, SESSION_ID, principal);
    }

    private void connect(WebSocketSession session, String sessionId, RealtimePrincipal connecting) throws Exception {
        registry.decorate(webSocketHandler).afterConnectionEstablished(session);
        registry.onSessionConnected(new SessionConnectedEvent(this,
                frame(sessionId, StompCommand.CONNECTED, null, null), connecting));
    }

    private void close(WebSocketSession session) throws Exception {
        registry.decorate(webSocketHandler).afterConnectionClosed(session, CloseStatus.NORMAL);
    }

    private void openSecondSession() throws Exception {
        when(secondWebSocketSession.getId()).thenReturn(SECOND_SESSION_ID);
        connect(secondWebSocketSession, SECOND_SESSION_ID, principal);
    }

    private RealtimePresenceChangedEvent presenceEvent(boolean online) {
        return new RealtimePresenceChangedEvent(principal.organizationId(), principal.userId(), online);
    }

    private static Message<byte[]> frame(StompCommand command, String subscriptionId, String destination) {
        return frame(SESSION_ID, command, subscriptionId, destination);
    }

    private static Message<byte[]> frame(String sessionId, StompCommand command, String subscriptionId,
                                         String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void subscribe(String subscriptionId, String destination) {
        registry.onSessionSubscribe(new SessionSubscribeEvent(this,
                frame(StompCommand.SUBSCRIBE, subscriptionId, destination), principal));
    }

    @Test
    void holdsSubscription_afterSubscribe_isTrueForThatIdOnly() {
        // Act
        subscribe("sub-1", FIRST_DESTINATION);

        // Assert
        assertThat(registry.holdsSubscription(SESSION_ID, "sub-1")).isTrue();
        assertThat(registry.holdsSubscription(SESSION_ID, "sub-2")).isFalse();
    }

    @Test
    void holdsSubscription_afterUnsubscribe_isFalse() {
        // Arrange
        subscribe("sub-1", FIRST_DESTINATION);

        // Act
        registry.onSessionUnsubscribe(new SessionUnsubscribeEvent(this,
                frame(StompCommand.UNSUBSCRIBE, "sub-1", null), principal));

        // Assert
        assertThat(registry.holdsSubscription(SESSION_ID, "sub-1")).isFalse();
        assertThat(registry.subscriptionCount(SESSION_ID)).isZero();
    }

    @Test
    void holdsSubscription_unknownSessionOrMissingId_isFalse() {
        // Arrange
        subscribe("sub-1", FIRST_DESTINATION);

        // Act and Assert
        assertThat(registry.holdsSubscription("other-session", "sub-1")).isFalse();
        assertThat(registry.holdsSubscription(null, "sub-1")).isFalse();
        assertThat(registry.holdsSubscription(SESSION_ID, null)).isFalse();
    }

    @Test
    void onSessionSubscribe_reusedId_keepsTheFirstDestinationAsTheBrokerDoes() {
        // Arrange
        subscribe("sub-1", FIRST_DESTINATION);
        when(realtimeDestinationAuthorizerRegistry.authorizeSubscribe(principal, FIRST_DESTINATION))
                .thenReturn(RealtimeFrameDecision.pass());

        // Act
        subscribe("sub-1", SECOND_DESTINATION);
        int revoked = registry.revalidateSubscriptions(List.of(principal.userId()));

        // Assert
        assertThat(registry.subscriptionCount(SESSION_ID)).isEqualTo(1);
        assertThat(revoked).isZero();
        verify(realtimeDestinationAuthorizerRegistry).authorizeSubscribe(principal, FIRST_DESTINATION);
        verify(realtimeDestinationAuthorizerRegistry, never()).authorizeSubscribe(principal, SECOND_DESTINATION);
    }

    @Test
    void onSessionConnected_firstSessionOfUser_publishesOnlineOnce() {
        // Arrange and Act: setUp opened and connected the user's first session

        // Assert
        verify(applicationEventPublisher).publishEvent(presenceEvent(true));
        verifyNoMoreInteractions(applicationEventPublisher);
        assertThat(registry.onlineUserIds(principal.organizationId())).containsExactly(principal.userId());
    }

    @Test
    void onSessionConnected_secondSessionOfSameUser_publishesNothing() throws Exception {
        // Act
        openSecondSession();

        // Assert
        verify(applicationEventPublisher).publishEvent(presenceEvent(true));
        verifyNoMoreInteractions(applicationEventPublisher);
        assertThat(registry.onlineUserIds(principal.organizationId())).containsExactly(principal.userId());
    }

    @Test
    void afterConnectionClosed_oneOfTwoSessions_publishesNothingAndKeepsUserOnline() throws Exception {
        // Arrange
        openSecondSession();

        // Act
        close(webSocketSession);

        // Assert
        verify(applicationEventPublisher).publishEvent(presenceEvent(true));
        verifyNoMoreInteractions(applicationEventPublisher);
        assertThat(registry.onlineUserIds(principal.organizationId())).containsExactly(principal.userId());
    }

    @Test
    void afterConnectionClosed_lastSession_publishesOfflineAndRemovesUserFromOnline() throws Exception {
        // Arrange
        openSecondSession();
        close(webSocketSession);

        // Act
        close(secondWebSocketSession);

        // Assert
        verify(applicationEventPublisher).publishEvent(presenceEvent(true));
        verify(applicationEventPublisher).publishEvent(presenceEvent(false));
        verifyNoMoreInteractions(applicationEventPublisher);
        assertThat(registry.onlineUserIds(principal.organizationId())).isEmpty();
        assertThat(registry.openSessionCount()).isZero();
    }

    @Test
    void onSessionConnected_connectionClosesBeforePresenceIsAdded_leavesNoGhostPresence() throws Exception {
        // Arrange: a user of another organization whose socket closes after the CONNECTED event
        // found the session but before presence was recorded. The first read of the organization
        // id is the one presence is recorded under, so the close is run from there.
        RealtimePrincipal racing = spy(new RealtimePrincipal(UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plusSeconds(300)));
        UUID racingOrganizationId = racing.organizationId();
        AtomicBoolean closed = new AtomicBoolean(false);
        doAnswer((InvocationOnMock invocation) -> {
            if (closed.compareAndSet(false, true)) {
                close(secondWebSocketSession);
            }
            return invocation.callRealMethod();
        }).when(racing).organizationId();
        when(secondWebSocketSession.getId()).thenReturn(SECOND_SESSION_ID);

        // Act
        connect(secondWebSocketSession, SECOND_SESSION_ID, racing);

        // Assert
        assertThat(closed).isTrue();
        assertThat(registry.onlineUserIds(racingOrganizationId)).isEmpty();
        verify(applicationEventPublisher).publishEvent(presenceEvent(true));
        verifyNoMoreInteractions(applicationEventPublisher);
    }

    private int acceptedSends(String sessionId, String bucket, int frames) {
        int accepted = 0;
        for (int index = 0; index < frames; index++) {
            if (registry.tryAcquireSend(sessionId, bucket, SEND_MAX, WINDOW_MS)) {
                accepted++;
            }
        }
        return accepted;
    }

    @Test
    void tryAcquireSend_pastTheMaxInTheWindow_dropsTheRest() {
        assertThat(acceptedSends(SESSION_ID, BUCKET, 5)).isEqualTo(SEND_MAX);
    }

    @Test
    void tryAcquireSend_otherSession_hasItsOwnWindow() throws Exception {
        // Arrange
        openSecondSession();
        acceptedSends(SESSION_ID, BUCKET, SEND_MAX);

        // Act and Assert
        assertThat(registry.tryAcquireSend(SECOND_SESSION_ID, BUCKET, SEND_MAX, WINDOW_MS)).isTrue();
    }

    @Test
    void tryAcquireSend_otherBucket_hasItsOwnWindow() {
        // Arrange
        acceptedSends(SESSION_ID, BUCKET, SEND_MAX);

        // Act and Assert
        assertThat(registry.tryAcquireSend(SESSION_ID, OTHER_BUCKET, SEND_MAX, WINDOW_MS)).isTrue();
    }

    @Test
    void tryAcquireSend_oldestFrameLeavesTheWindow_nextFramePasses() {
        // Arrange
        registry.tryAcquireSend(SESSION_ID, BUCKET, SEND_MAX, WINDOW_MS);
        clock.advanceMillis(1_000L);
        acceptedSends(SESSION_ID, BUCKET, 2);

        // Act: exactly one window after the first frame, it no longer counts
        clock.advanceMillis(9_000L);

        // Assert
        assertThat(registry.tryAcquireSend(SESSION_ID, BUCKET, SEND_MAX, WINDOW_MS)).isTrue();
        assertThat(registry.tryAcquireSend(SESSION_ID, BUCKET, SEND_MAX, WINDOW_MS)).isFalse();
    }

    @Test
    void tryAcquireSend_oneFramePerInterval_acceptsExactlyAtLastPlusInterval() {
        // Arrange
        assertThat(registry.tryAcquireSend(SESSION_ID, BUCKET, 1, WINDOW_MS)).isTrue();

        // Act and Assert: one millisecond early is dropped, the interval itself is accepted
        clock.advanceMillis(WINDOW_MS - 1);
        assertThat(registry.tryAcquireSend(SESSION_ID, BUCKET, 1, WINDOW_MS)).isFalse();
        clock.advanceMillis(1L);
        assertThat(registry.tryAcquireSend(SESSION_ID, BUCKET, 1, WINDOW_MS)).isTrue();
    }

    @Test
    void tryAcquireSend_unknownSession_isRefused() {
        assertThat(registry.tryAcquireSend("other-session", BUCKET, SEND_MAX, WINDOW_MS)).isFalse();
        assertThat(registry.tryAcquireSend(null, BUCKET, SEND_MAX, WINDOW_MS)).isFalse();
    }

    @Test
    void tryAcquireSend_sessionClosedAndReopened_startsWithAnEmptyWindow() throws Exception {
        // Arrange
        acceptedSends(SESSION_ID, BUCKET, SEND_MAX);
        close(webSocketSession);

        // Act
        connect(webSocketSession, SESSION_ID, principal);

        // Assert
        assertThat(registry.tryAcquireSend(SESSION_ID, BUCKET, SEND_MAX, WINDOW_MS)).isTrue();
    }

    @Test
    void allowsSubscription_belowTheCap_isTrue() {
        // Arrange
        subscribe("sub-1", FIRST_DESTINATION);

        // Act and Assert
        assertThat(registry.allowsSubscription(SESSION_ID)).isTrue();
    }

    @Test
    void allowsSubscription_atTheCap_isFalse() {
        // Arrange
        subscribe("sub-1", FIRST_DESTINATION);
        subscribe("sub-2", SECOND_DESTINATION);

        // Act and Assert
        assertThat(registry.allowsSubscription(SESSION_ID)).isFalse();
    }

    @Nested
    class OnAccessRevoked {

        private static final String OTHER_ORGANIZATION_SESSION_ID = "session-other-organization";
        private static final String OTHER_USER_SESSION_ID = "session-other-user";
        private static final String HANDSHAKE_ONLY_SESSION_ID = "session-handshake-only";

        private final MessageChannel outbound = mock(MessageChannel.class);
        private final WebSocketSession otherOrganizationSession = mock(WebSocketSession.class);
        private final WebSocketSession otherUserSession = mock(WebSocketSession.class);

        @BeforeEach
        void openSessionsAroundTheRevokedOne() throws Exception {
            lenient().when(clientOutboundChannel.getObject()).thenReturn(outbound);
            when(otherOrganizationSession.getId()).thenReturn(OTHER_ORGANIZATION_SESSION_ID);
            when(otherUserSession.getId()).thenReturn(OTHER_USER_SESSION_ID);
            connect(otherOrganizationSession, OTHER_ORGANIZATION_SESSION_ID,
                    new RealtimePrincipal(principal.userId(), UUID.randomUUID(), Instant.now().plusSeconds(300)));
            connect(otherUserSession, OTHER_USER_SESSION_ID,
                    new RealtimePrincipal(UUID.randomUUID(), principal.organizationId(), Instant.now().plusSeconds(300)));
        }

        private Set<String> sessionsSentForbidden() {
            ArgumentCaptor<Message<?>> sent = ArgumentCaptor.captor();
            verify(outbound, atLeastOnce()).send(sent.capture());
            return sent.getAllValues().stream()
                    .map(StompHeaderAccessor::wrap)
                    .peek((StompHeaderAccessor accessor) -> {
                        assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
                        assertThat(accessor.getMessage())
                                .isEqualTo(ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN.getCode());
                    })
                    .map(StompHeaderAccessor::getSessionId)
                    .collect(Collectors.toSet());
        }

        @Test
        void memberEvent_closesOnlyThatUsersSessionsInThatOrganization() {
            // Act
            registry.onAccessRevoked(AccessRevokedEvent.member(principal.userId(), principal.organizationId()));

            // Assert
            assertThat(sessionsSentForbidden()).containsExactly(SESSION_ID);
        }

        @Test
        void userEvent_closesThatUsersSessionsInEveryOrganization() {
            // Act
            registry.onAccessRevoked(AccessRevokedEvent.user(principal.userId()));

            // Assert
            assertThat(sessionsSentForbidden()).containsExactlyInAnyOrder(SESSION_ID, OTHER_ORGANIZATION_SESSION_ID);
        }

        @Test
        void organizationEvent_closesEverySessionOfThatOrganization() {
            // Act
            registry.onAccessRevoked(AccessRevokedEvent.organization(principal.organizationId()));

            // Assert
            assertThat(sessionsSentForbidden()).containsExactlyInAnyOrder(SESSION_ID, OTHER_USER_SESSION_ID);
        }

        @Test
        void kazanLost_closesOnlyThatUsersSessionsInThatOrganization() {
            // Act
            registry.onAppAccessRevoked(
                    new AppAccessRevokedEvent(principal.userId(), principal.organizationId(), App.KAZAN));

            // Assert
            assertThat(sessionsSentForbidden()).containsExactly(SESSION_ID);
        }

        @Test
        void appWithoutRealtimeLost_closesNothing() {
            // Act
            registry.onAppAccessRevoked(
                    new AppAccessRevokedEvent(principal.userId(), principal.organizationId(), App.KAYA));

            // Assert
            verify(outbound, never()).send(any());
        }

        @Test
        void eventForNobodyConnected_closesNothing() {
            // Act
            registry.onAccessRevoked(AccessRevokedEvent.user(UUID.randomUUID()));

            // Assert
            verify(outbound, never()).send(any());
        }

        @Test
        void repeatedEvent_sendsOneErrorPerSession() {
            // Act
            registry.onAccessRevoked(AccessRevokedEvent.member(principal.userId(), principal.organizationId()));
            registry.onAccessRevoked(AccessRevokedEvent.member(principal.userId(), principal.organizationId()));

            // Assert
            verify(outbound).send(any());
        }

        @Test
        void socketPastTheHandshakeBeforeConnect_isClosedByItsTicketPrincipal() throws Exception {
            // Arrange
            WebSocketSession handshakeOnly = mock(WebSocketSession.class);
            when(handshakeOnly.getId()).thenReturn(HANDSHAKE_ONLY_SESSION_ID);
            Map<String, Object> attributes = new HashMap<>();
            attributes.put(RealtimeConstant.SESSION_PRINCIPAL_ATTRIBUTE,
                    new RealtimePrincipal(UUID.randomUUID(), principal.organizationId(), Instant.now().plusSeconds(300)));
            when(handshakeOnly.getAttributes()).thenReturn(attributes);
            registry.decorate(webSocketHandler).afterConnectionEstablished(handshakeOnly);

            // Act
            registry.onAccessRevoked(AccessRevokedEvent.organization(principal.organizationId()));

            // Assert
            assertThat(sessionsSentForbidden())
                    .containsExactlyInAnyOrder(SESSION_ID, OTHER_USER_SESSION_ID, HANDSHAKE_ONLY_SESSION_ID);
        }
    }

    /**
     * Compares this registry's presence with Spring's {@link DefaultSimpUserRegistry} fed the same
     * transport events in the order the transport produces them: the CONNECTED event, and on close
     * first the decorator's close callback, then the {@link SessionDisconnectEvent} of the STOMP
     * handler.
     */
    @Nested
    class PresenceAgainstSimpUserRegistry {

        private final DefaultSimpUserRegistry simpUserRegistry = new DefaultSimpUserRegistry();

        @BeforeEach
        void feedTheFirstConnect() {
            springConnect(SESSION_ID, principal);
        }

        private void springConnect(String sessionId, RealtimePrincipal connecting) {
            simpUserRegistry.onApplicationEvent(new SessionConnectedEvent(this,
                    frame(sessionId, StompCommand.CONNECTED, null, null), connecting));
        }

        private void springDisconnect(String sessionId) {
            simpUserRegistry.onApplicationEvent(new SessionDisconnectEvent(this,
                    frame(sessionId, StompCommand.DISCONNECT, null, null), sessionId, CloseStatus.NORMAL, principal));
        }

        private void closeLikeTheTransport(WebSocketSession session, String sessionId) throws Exception {
            close(session);
            springDisconnect(sessionId);
        }

        private Set<UUID> springOnline(UUID organizationId) {
            String suffix = ":" + organizationId;
            return simpUserRegistry.getUsers().stream()
                    .map(SimpUser::getName)
                    .filter((String name) -> name.endsWith(suffix))
                    .map((String name) -> UUID.fromString(name.substring(0, name.length() - suffix.length())))
                    .collect(Collectors.toSet());
        }

        private Set<String> springSessionsOtherThan(String sessionId) {
            SimpUser user = simpUserRegistry.getUser(principal.getName());
            if (user == null) {
                return Set.of();
            }
            return user.getSessions().stream()
                    .map(SimpSession::getId)
                    .filter((String id) -> !id.equals(sessionId))
                    .collect(Collectors.toSet());
        }

        private void assertSameOnline() {
            assertThat(springOnline(principal.organizationId()))
                    .isEqualTo(registry.onlineUserIds(principal.organizationId()));
        }

        @Test
        void connect_bothShowTheUserOnline() {
            assertSameOnline();
            assertThat(registry.onlineUserIds(principal.organizationId())).containsExactly(principal.userId());
        }

        @Test
        void secondTab_bothStillShowTheUserOnce() throws Exception {
            // Act
            openSecondSession();
            springConnect(SECOND_SESSION_ID, principal);

            // Assert
            assertSameOnline();
        }

        @Test
        void oneOfTwoTabsClosed_bothKeepTheUserOnline() throws Exception {
            // Arrange
            openSecondSession();
            springConnect(SECOND_SESSION_ID, principal);

            // Act
            closeLikeTheTransport(webSocketSession, SESSION_ID);

            // Assert
            assertSameOnline();
            assertThat(registry.onlineUserIds(principal.organizationId())).containsExactly(principal.userId());
        }

        @Test
        void lastTabClosed_bothShowTheUserOffline() throws Exception {
            // Arrange
            openSecondSession();
            springConnect(SECOND_SESSION_ID, principal);
            closeLikeTheTransport(webSocketSession, SESSION_ID);

            // Act
            closeLikeTheTransport(secondWebSocketSession, SECOND_SESSION_ID);

            // Assert
            assertSameOnline();
            assertThat(registry.onlineUserIds(principal.organizationId())).isEmpty();
        }

        @Test
        void expiryClose_bothShowTheUserOffline() throws Exception {
            // Arrange
            when(clientOutboundChannel.getObject()).thenReturn(mock(MessageChannel.class));
            clock.advanceMillis(301_000L);
            RealtimePrincipal expiring = new RealtimePrincipal(principal.userId(), principal.organizationId(),
                    clock.instant().minusSeconds(1));
            close(webSocketSession);
            springDisconnect(SESSION_ID);
            connect(webSocketSession, SESSION_ID, expiring);
            springConnect(SESSION_ID, expiring);

            // Act: the sweep sends the ERROR frame, after which the transport closes the socket
            registry.closeExpiredSessions();
            closeLikeTheTransport(webSocketSession, SESSION_ID);

            // Assert
            assertSameOnline();
            assertThat(registry.onlineUserIds(principal.organizationId())).isEmpty();
        }

        @Test
        void twoTabsClosingAtOnce_springRegistryNeverShowsALastSessionWhileThisRegistryAnnouncesOfflineOnce()
                throws Exception {
            // Arrange
            openSecondSession();
            springConnect(SECOND_SESSION_ID, principal);

            // Act: both close callbacks run before either SessionDisconnectEvent, as when two
            // sockets of the user close on two transport threads at the same moment
            Set<String> othersSeenByFirstClose = springSessionsOtherThan(SESSION_ID);
            close(webSocketSession);
            Set<String> othersSeenBySecondClose = springSessionsOtherThan(SECOND_SESSION_ID);
            close(secondWebSocketSession);
            springDisconnect(SESSION_ID);
            springDisconnect(SECOND_SESSION_ID);

            // Assert: a transition read from Spring's registry would announce no one offline
            assertThat(othersSeenByFirstClose).containsExactly(SECOND_SESSION_ID);
            assertThat(othersSeenBySecondClose).containsExactly(SESSION_ID);
            verify(applicationEventPublisher).publishEvent(presenceEvent(true));
            verify(applicationEventPublisher).publishEvent(presenceEvent(false));
            verifyNoMoreInteractions(applicationEventPublisher);
            assertSameOnline();
        }
    }

    /**
     * A clock the test moves forward by hand.
     */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advanceMillis(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
