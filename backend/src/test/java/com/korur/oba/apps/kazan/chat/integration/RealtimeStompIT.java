package com.korur.oba.apps.kazan.chat.integration;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// 2. Third-party
import org.awaitility.Awaitility;

// 3. Java SDK
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.chat.integration.RealtimeStompTestApplication.ScopedAuthorizer;
import com.korur.oba.apps.kazan.chat.integration.RealtimeStompTestApplication.TestSignal;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.connect.realtime.service.RealtimeSignalBus;
import com.korur.oba.connect.realtime.service.RealtimeTicketService;
import com.korur.oba.connect.realtime.util.RealtimeDestinationUtil;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.event.AppAccessRevokedEvent;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.event.AccessRevokedEvent;

/**
 * The realtime channel over a real WebSocket, on a slice of the application that holds only the
 * realtime stack (see {@link RealtimeStompTestApplication}). It needs neither the local database nor
 * Docker: each client gets a ticket from the real ticket service, under the access token a REST
 * request would carry, and opens the socket on the embedded server with it, through the real
 * handshake. Only the chat channel lookup is a mock; everything else is the production wiring.
 * The membership check that guards a ticket runs on the REST request, before the ticket service,
 * so it is not part of this slice.
 *
 * The expiry sweep is shortened so a session whose token runs out is seen being closed within
 * seconds. Delivery assertions never sleep: a later sentinel signal, published after the one that
 * must not arrive, proves the earlier one would have been delivered first.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = RealtimeStompTestApplication.class,
        properties = "oba.realtime.expiry-sweep-ms=300")
class RealtimeStompIT {

    private static final String UNAUTHENTICATED = "RTM_SEC_001";
    private static final String FORBIDDEN = "RTM_SEC_002";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @LocalServerPort
    private int port;

    @Autowired
    private RealtimeSignalBus realtimeSignalBus;

    @Autowired
    private RealtimeSessionRegistry realtimeSessionRegistry;

    @Autowired
    private RealtimeTicketService realtimeTicketService;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @Autowired
    private RealtimeStompTestApplication.TypingStub typingStub;

    @Autowired
    private ScopedAuthorizer scopedAuthorizer;

    @MockitoBean
    private ChatChannelQueryService chatChannelQueryService;

    @MockitoBean
    private AppAccessQueryService appAccessQueryService;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID otherOrganizationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final List<RawStompClient> rawClients = new CopyOnWriteArrayList<>();
    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUpClient() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        when(appAccessQueryService.hasAppAccess(any(UUID.class), any(UUID.class), eq(App.KAZAN))).thenReturn(true);
    }

    @AfterEach
    void disconnectClients() throws IOException {
        for (Client client : clients) {
            client.disconnect();
        }
        for (RawStompClient rawClient : rawClients) {
            rawClient.close();
        }
        stompClient.stop();
        scopedAuthorizer.reset();
        SecurityContextHolder.clearContext();
    }

    private static String chatTopic(UUID organization) {
        return RealtimeDestinationUtil.organizationTopic(organization, ChatConstant.CHAT_TOPIC_SUFFIX);
    }

    private static String scopedTopic(UUID organization) {
        return RealtimeDestinationUtil.organizationTopic(organization, ScopedAuthorizer.SUFFIX);
    }

    @Test
    void connect_validTicket_receivesConnectedFrame() {
        Client client = connect(userId, organizationId);

        client.awaitConnected();

        assertThat(client.session).isNotNull();
        assertThat(client.errors).isEmpty();
    }

    @Test
    void handshake_withoutTicket_isRefusedWith401() {
        assertHandshakeRefused(endpoint(null));
    }

    @Test
    void handshake_unknownTicket_isRefusedWith401() {
        assertHandshakeRefused(endpoint("never-issued"));
    }

    @Test
    void handshake_ticketUsedTwice_secondHandshakeIsRefusedWith401() {
        String ticket = issueTicket(userId, organizationId, Instant.now().plusSeconds(3_600));
        Client first = connectWithTicket(ticket);
        first.awaitConnected();

        assertHandshakeRefused(endpoint(ticket));
    }

    @Test
    void connect_secondConnectOnTheSameSocket_getsErrorAndTheSocketCloses() throws Exception {
        RawStompClient rawClient = connectRaw(userId, organizationId);

        // Spring's STOMP handler refuses a second CONNECT of a session before the inbound
        // channel sees it, so the ERROR carries its own message; the session user never changes.

        rawClient.sendFrame("CONNECT\n"
                + "accept-version:1.2\n"
                + "heart-beat:0,0\n\n");

        rawClient.awaitFrame("ERROR", null);
        Awaitility.await().atMost(WAIT).untilAsserted(() -> assertThat(rawClient.session.isOpen()).isFalse());
    }

    @Test
    void ticketRevokedBeforeUse_handshakeIsRefusedWith401() {
        String ticket = issueTicket(userId, organizationId, Instant.now().plusSeconds(3_600));

        realtimeTicketService.onAccessRevoked(AccessRevokedEvent.member(userId, organizationId));

        assertHandshakeRefused(endpoint(ticket));
    }

    @Test
    void subscribe_otherOrganizationTopic_getsForbiddenError() {
        Client client = connect(userId, organizationId);
        client.awaitConnected();

        client.session.subscribe(chatTopic(otherOrganizationId), client.frameHandler());

        client.awaitError(FORBIDDEN);
    }

    @Test
    void send_anythingButTyping_getsForbiddenError() {
        Client client = connect(userId, organizationId);
        client.awaitConnected();

        client.session.send("/app/chat/messages", "{}");

        client.awaitError(FORBIDDEN);
    }

    @Test
    void send_typingDestination_reachesTheReceiver() {
        Client client = connect(userId, organizationId);
        client.awaitConnected();

        client.session.send("/app/chat/typing", "{}");

        Awaitility.await().atMost(WAIT).untilAsserted(() ->
                assertThat(typingStub.receivedCount()).isPositive());
    }

    @Test
    void publishToOrganization_reachesOnlySessionsOfThatOrganization() {
        Client inOrganization = connectAndSubscribe(userId, organizationId);
        Client inOtherOrganization = connectAndSubscribe(otherUserId, otherOrganizationId);

        realtimeSignalBus.publishToTopic(chatTopic(organizationId), TestSignal.of("for-a"));
        realtimeSignalBus.publishToTopic(chatTopic(otherOrganizationId), TestSignal.of("sentinel-b"));

        inOrganization.awaitSignal("for-a");
        inOtherOrganization.awaitSignal("sentinel-b");
        assertThat(inOrganization.marks()).containsExactly("for-a");
        assertThat(inOtherOrganization.marks()).containsExactly("sentinel-b");
    }

    @Test
    void publishToUser_reachesOnlyThatUsersSessionOfThatOrganization() {
        Client target = connectAndSubscribe(userId, organizationId);
        Client otherUser = connectAndSubscribe(otherUserId, otherOrganizationId);

        realtimeSignalBus.publishToUser(userId, organizationId, ChatConstant.CHAT_USER_QUEUE, TestSignal.of("private"));
        realtimeSignalBus.publishToUser(otherUserId, otherOrganizationId, ChatConstant.CHAT_USER_QUEUE,
                TestSignal.of("sentinel"));

        target.awaitSignal("private");
        otherUser.awaitSignal("sentinel");
        assertThat(target.marks()).containsExactly("private");
        assertThat(otherUser.marks()).containsExactly("sentinel");
    }

    @Test
    void presence_firstConnectIsAnnouncedToTheOrganizationTopic() {
        Client watcher = connectAndSubscribe(userId, organizationId);
        UUID joiningUser = UUID.randomUUID();

        Client joiner = connect(joiningUser, organizationId);
        joiner.awaitConnected();

        Awaitility.await().atMost(WAIT).untilAsserted(() ->
                assertThat(watcher.signals).anyMatch((Map<String, Object> signal) ->
                        "PRESENCE".equals(signal.get("type"))
                                && joiningUser.toString().equals(signal.get("userId"))
                                && Boolean.TRUE.equals(signal.get("online"))));
        assertThat(realtimeSessionRegistry.onlineUserIds(organizationId)).contains(joiningUser);
    }

    @Test
    void expiredToken_sessionIsClosedBySweepWithUnauthenticatedError() {
        Client client = connectWithTicket(issueTicket(userId, organizationId, Instant.now().plusSeconds(2)));
        client.awaitConnected();

        client.awaitError(UNAUTHENTICATED);
    }

    @Test
    void onAccessRevoked_revokedMember_getsForbiddenErrorWhileOthersStay() {
        Client revoked = connectAndSubscribe(userId, organizationId);
        Client otherOrganization = connectAndSubscribe(otherUserId, otherOrganizationId);

        realtimeSessionRegistry.onAccessRevoked(AccessRevokedEvent.member(userId, organizationId));

        revoked.awaitError(FORBIDDEN);
        realtimeSignalBus.publishToTopic(chatTopic(otherOrganizationId), TestSignal.of("still-open"));
        otherOrganization.awaitSignal("still-open");
        assertThat(otherOrganization.errors).isEmpty();
    }

    @Test
    void onAppAccessRevoked_kazanLost_closesThatUsersSessionWithForbiddenError() {
        Client revoked = connectAndSubscribe(userId, organizationId);
        Client sameOrganization = connectAndSubscribe(otherUserId, organizationId);

        realtimeSessionRegistry.onAppAccessRevoked(new AppAccessRevokedEvent(userId, organizationId, App.KAZAN));

        revoked.awaitError(FORBIDDEN);
        realtimeSignalBus.publishToTopic(chatTopic(organizationId), TestSignal.of("still-open"));
        sameOrganization.awaitSignal("still-open");
        assertThat(sameOrganization.errors).isEmpty();
    }

    @Test
    void onAppAccessRevoked_kayaLost_keepsTheSessionOpen() {
        Client client = connectAndSubscribe(userId, organizationId);

        realtimeSessionRegistry.onAppAccessRevoked(new AppAccessRevokedEvent(userId, organizationId, App.KAYA));

        realtimeSignalBus.publishToTopic(chatTopic(organizationId), TestSignal.of("still-open"));
        client.awaitSignal("still-open");
        assertThat(client.errors).isEmpty();
        assertThat(realtimeSessionRegistry.onlineUserIds(organizationId)).contains(userId);
    }

    @Test
    void onAccessRevoked_organizationDisabled_closesEverySessionOfThatOrganization() {
        Client first = connectAndSubscribe(userId, organizationId);
        Client second = connectAndSubscribe(otherUserId, organizationId);

        realtimeSessionRegistry.onAccessRevoked(AccessRevokedEvent.organization(organizationId));

        first.awaitError(FORBIDDEN);
        second.awaitError(FORBIDDEN);
    }

    @Test
    void subscribe_wildcardPattern_getsForbiddenError() {
        Client client = connect(userId, organizationId);
        client.awaitConnected();

        client.session.subscribe("/topic/organizations/*/chat", client.frameHandler());

        client.awaitError(FORBIDDEN);
    }

    @Test
    void subscribe_scopedTopicTheConsumerDenies_keepsTheSessionAndDeliversNothing() {
        Client client = connectAndSubscribe(userId, organizationId);
        scopedAuthorizer.deny(userId);
        int checksBefore = scopedAuthorizer.subscribeChecks();

        client.session.subscribe(scopedTopic(organizationId), client.frameHandler());
        Awaitility.await().atMost(WAIT).untilAsserted(() ->
                assertThat(scopedAuthorizer.subscribeChecks()).isGreaterThan(checksBefore));
        realtimeSignalBus.publishToTopic(scopedTopic(organizationId), TestSignal.of("scoped"));
        realtimeSignalBus.publishToTopic(chatTopic(organizationId), TestSignal.of("chat-sentinel"));

        client.awaitSignal("chat-sentinel");
        assertThat(client.marks()).containsExactly("chat-sentinel");
        assertThat(client.errors).isEmpty();
        assertThat(client.session.isConnected()).isTrue();
    }

    @Test
    void revalidateSubscriptions_revokedScopedTopicStopsWhileChatKeepsReceiving() {
        Client client = connectAndSubscribe(userId, organizationId);
        client.session.subscribe(scopedTopic(organizationId), client.frameHandler());
        awaitSubscriptions(userId + ":" + organizationId, 3);
        realtimeSignalBus.publishToTopic(scopedTopic(organizationId), TestSignal.of("scoped-before"));
        client.awaitSignal("scoped-before");

        scopedAuthorizer.deny(userId);
        int revoked = realtimeSessionRegistry.revalidateSubscriptions(List.of(userId));
        realtimeSignalBus.publishToTopic(scopedTopic(organizationId), TestSignal.of("scoped-after"));
        realtimeSignalBus.publishToTopic(chatTopic(organizationId), TestSignal.of("chat-after"));

        assertThat(revoked).isEqualTo(1);
        client.awaitSignal("chat-after");
        assertThat(client.marks()).containsExactly("scoped-before", "chat-after");
        assertThat(client.errors).isEmpty();
    }

    @Test
    void send_floodPastTheRateLimit_isDroppedAndTheSessionStays() {
        Client client = connectAndSubscribe(userId, organizationId);
        int receivedBefore = typingStub.receivedCount();

        for (int index = 0; index < 25; index++) {
            client.session.send("/app/chat/typing", "{}");
        }
        // A frame sent after the flood is read after it, so once this subscription is tracked
        // every flood frame has been let through or dropped.
        client.session.subscribe(scopedTopic(organizationId), client.frameHandler());
        awaitSubscriptions(userId + ":" + organizationId, 3);

        Awaitility.await().atMost(WAIT).during(Duration.ofMillis(500)).untilAsserted(() ->
                assertThat(typingStub.receivedCount() - receivedBefore).isEqualTo(20));
        assertThat(client.errors).isEmpty();
        assertThat(client.session.isConnected()).isTrue();
    }

    @Test
    void clientMessageFrame_toAnotherOrganizationsChatTopic_getsForbiddenErrorAndIsNotDelivered() throws Exception {
        Client victim = connectAndSubscribe(otherUserId, otherOrganizationId);
        RawStompClient attacker = connectRaw(userId, organizationId);

        attacker.sendFrame("MESSAGE\n"
                + "destination:" + chatTopic(otherOrganizationId) + "\n"
                + "subscription:forged\n"
                + "message-id:forged-1\n"
                + "content-type:application/json\n\n"
                + "{\"type\":\"TEST\",\"mark\":\"forged\"}");

        attacker.awaitFrame("ERROR", FORBIDDEN);
        realtimeSignalBus.publishToTopic(chatTopic(otherOrganizationId), TestSignal.of("sentinel"));
        victim.awaitSignal("sentinel");
        assertThat(victim.marks()).containsExactly("sentinel");
    }

    @Test
    void subscribe_subscriptionIdTheSessionAlreadyHolds_getsForbiddenError() {
        Client client = connectAndSubscribe(userId, organizationId);
        StompHeaders scoped = new StompHeaders();
        scoped.setId("reused");
        scoped.setDestination(scopedTopic(organizationId));
        client.session.subscribe(scoped, client.frameHandler());
        awaitSubscriptions(userId + ":" + organizationId, 3);

        // Subscription ids are unique per connection; the broker would silently keep the scoped
        // topic under this id while the client believes it now receives the chat topic.
        StompHeaders reused = new StompHeaders();
        reused.setId("reused");
        reused.setDestination(chatTopic(organizationId));
        client.session.subscribe(reused, client.frameHandler());

        client.awaitError(FORBIDDEN);
    }

    private void awaitSubscriptions(String userKey, int count) {
        Awaitility.await().atMost(WAIT).untilAsserted(() -> {
            SimpUser simpUser = simpUserRegistry.getUser(userKey);
            assertThat(simpUser).as("realtime user " + userKey).isNotNull();
            assertThat(simpUser.getSessions().stream().mapToInt(s -> s.getSubscriptions().size()).sum())
                    .isEqualTo(count);
        });
    }

    /**
     * Issues a ticket as the ticket endpoint does, under the access token of the request.
     */
    private String issueTicket(UUID user, UUID organization, Instant tokenExpiresAt) {
        Jwt jwt = Jwt.withTokenValue("opaque")
                .header("alg", "none")
                .subject("kc-" + user)
                .issuedAt(Instant.now())
                .expiresAt(tokenExpiresAt)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        try {
            return realtimeTicketService.issueTicket(User.builder().id(user).build(), organization).ticket();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private URI endpoint(String ticket) {
        String query = ticket == null ? "" : "?ticket=" + ticket;
        return URI.create("ws://localhost:" + port + "/ws" + query);
    }

    private void assertHandshakeRefused(URI uri) {
        CompletableFuture<WebSocketSession> handshake =
                new StandardWebSocketClient().execute(new TextWebSocketHandler(), new WebSocketHttpHeaders(), uri);

        assertThatThrownBy(() -> handshake.get(WAIT.toSeconds(), TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasStackTraceContaining("401");
    }

    private Client connect(UUID user, UUID organization) {
        return connectWithTicket(issueTicket(user, organization, Instant.now().plusSeconds(3_600)));
    }

    private Client connectWithTicket(String ticket) {
        StompHeaders headers = new StompHeaders();
        headers.setHeartbeat(new long[] {0, 0});
        Client client = new Client();
        clients.add(client);
        stompClient.connectAsync(endpoint(ticket).toString(), new WebSocketHttpHeaders(), headers, client);
        return client;
    }

    private RawStompClient connectRaw(UUID user, UUID organization) throws Exception {
        RawStompClient rawClient = new RawStompClient();
        rawClients.add(rawClient);
        String ticket = issueTicket(user, organization, Instant.now().plusSeconds(3_600));
        rawClient.session = new StandardWebSocketClient()
                .execute(rawClient, new WebSocketHttpHeaders(), endpoint(ticket))
                .get(WAIT.toSeconds(), TimeUnit.SECONDS);
        rawClient.sendFrame("CONNECT\n"
                + "accept-version:1.2\n"
                + "heart-beat:0,0\n\n");
        rawClient.awaitFrame("CONNECTED", null);
        return rawClient;
    }

    private Client connectAndSubscribe(UUID user, UUID organization) {
        Client client = connect(user, organization);
        client.awaitConnected();
        client.session.subscribe(chatTopic(organization), client.frameHandler());
        client.session.subscribe("/user/queue/chat", client.frameHandler());
        String userKey = user + ":" + organization;
        Awaitility.await().atMost(WAIT).untilAsserted(() -> {
            SimpUser simpUser = simpUserRegistry.getUser(userKey);
            assertThat(simpUser).as("realtime user " + userKey).isNotNull();
            assertThat(simpUser.getSessions().stream().mapToInt(s -> s.getSubscriptions().size()).sum())
                    .isEqualTo(2);
        });
        return client;
    }

    /**
     * A STOMP client written by hand over a plain WebSocket, for frames the Spring STOMP client
     * never sends, such as MESSAGE, which only a broker may send.
     */
    private static final class RawStompClient extends TextWebSocketHandler {

        private volatile WebSocketSession session;
        private final List<String> frames = new CopyOnWriteArrayList<>();

        @Override
        protected void handleTextMessage(WebSocketSession webSocketSession, TextMessage message) {
            frames.add(message.getPayload());
        }

        void sendFrame(String frame) throws IOException {
            session.sendMessage(new TextMessage(frame + "\0"));
        }

        void awaitFrame(String command, String content) {
            Awaitility.await().atMost(WAIT).untilAsserted(() ->
                    assertThat(frames).anyMatch((String frame) -> frame.startsWith(command + "\n")
                            && (content == null || frame.contains(content))));
        }

        void close() throws IOException {
            WebSocketSession current = session;
            if (current != null && current.isOpen()) {
                current.close(CloseStatus.NORMAL);
            }
        }
    }

    /**
     * One STOMP client; records the session, ERROR frames and the delivered signals.
     */
    private static final class Client extends StompSessionHandlerAdapter {

        private volatile StompSession session;
        private final List<String> errors = new CopyOnWriteArrayList<>();
        private final List<Map<String, Object>> signals = new CopyOnWriteArrayList<>();

        @Override
        public void afterConnected(StompSession connected, StompHeaders connectedHeaders) {
            this.session = connected;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errors.add(String.valueOf(headers.getFirst("message")));
        }

        @Override
        public void handleTransportError(StompSession stompSession, Throwable exception) {
            errors.add("transport:" + exception.getClass().getSimpleName());
        }

        StompFrameHandler frameHandler() {
            return new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(StompHeaders headers, Object payload) {
                    Map<String, Object> signal = (Map<String, Object>) payload;
                    if ("TEST".equals(signal.get("type")) || "PRESENCE".equals(signal.get("type"))) {
                        signals.add(signal);
                    }
                }
            };
        }

        void awaitConnected() {
            Awaitility.await().atMost(WAIT).untilAsserted(() -> {
                assertThat(errors).as("errors before CONNECTED").isEmpty();
                assertThat(session).as("session").isNotNull();
            });
        }

        void awaitError(String code) {
            Awaitility.await().atMost(WAIT).untilAsserted(() ->
                    assertThat(errors).anyMatch((String error) -> error.contains(code)));
        }

        void awaitSignal(String mark) {
            Awaitility.await().atMost(WAIT).untilAsserted(() -> assertThat(marks()).contains(mark));
        }

        List<String> marks() {
            return signals.stream()
                    .filter((Map<String, Object> signal) -> "TEST".equals(signal.get("type")))
                    .map((Map<String, Object> signal) -> String.valueOf(signal.get("mark")))
                    .toList();
        }

        void disconnect() {
            StompSession current = session;
            if (current != null && current.isConnected()) {
                current.disconnect();
            }
        }
    }
}
