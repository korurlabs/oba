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
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.messaging.WebSocketStompClient;

// 2. Third-party
import org.awaitility.Awaitility;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
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
import com.korur.oba.apps.kazan.chat.integration.RealtimeStompTestApplication.TestSignal;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.connect.realtime.service.RealtimeSignalBus;
import com.korur.oba.connect.realtime.service.RealtimeTicketService;
import com.korur.oba.connect.realtime.util.RealtimeDestinationUtil;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * How the realtime transport copes with clients that stop behaving: a connection that goes
 * silent without DISCONNECT, and a consumer that stops reading while signals keep coming. Both
 * misbehaving clients are plain TCP sockets, because a real STOMP client always reads and
 * answers heartbeats.
 *
 * The heartbeat is shortened by property so a missed heartbeat is seen within a second. The slow
 * consumer test fills the kernel buffers of a socket that never reads, so it ends when the server
 * gives up on that socket at its send buffer or its send time limit, whichever comes first.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = RealtimeStompTestApplication.class,
        properties = {"oba.realtime.heartbeat-ms=200", "oba.realtime.expiry-sweep-ms=600000"})
class RealtimeStompResilienceIT {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final Duration SLOW_CONSUMER_WAIT = Duration.ofSeconds(60);
    // A frame must stay under the 8 KiB text buffer of the WebSocket client, or the well-behaved
    // subscriber would be closed too; the volume comes from the number of frames.
    private static final int BURST_FRAMES = 3_000;
    private static final int FRAME_PADDING_CHARS = 6_000;

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

    @MockitoBean
    private ChatChannelQueryService chatChannelQueryService;

    @MockitoBean
    private AppAccessQueryService appAccessQueryService;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID watcherId = UUID.randomUUID();
    private final UUID otherWatcherId = UUID.randomUUID();
    private final UUID stalledUserId = UUID.randomUUID();

    private final List<Watcher> watchers = new CopyOnWriteArrayList<>();
    private final List<StalledSocket> stalledSockets = new CopyOnWriteArrayList<>();
    private WebSocketStompClient stompClient;

    @BeforeEach
    void allowKazan() {
        when(appAccessQueryService.hasAppAccess(any(UUID.class), any(UUID.class), eq(App.KAZAN))).thenReturn(true);
    }

    @AfterEach
    void closeClients() throws IOException {
        for (Watcher watcher : watchers) {
            watcher.disconnect();
        }
        for (StalledSocket stalled : stalledSockets) {
            stalled.close();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
        SecurityContextHolder.clearContext();
    }

    @Test
    void silentConnection_missedHeartbeats_removeSessionSubscriptionsAndPresenceAndAnnounceOfflineOnce() throws Exception {
        Watcher watcher = connectWatcher(watcherId);
        StalledSocket stalled = openStalledSocket(stalledUserId, "200,200");
        stalled.subscribe(chatTopic(), "stalled-sub");
        String userKey = stalledUserId + ":" + organizationId;
        awaitSubscriptions(userKey, 1);
        String sessionId = simpUserRegistry.getUser(userKey).getSessions().iterator().next().getId();
        assertThat(realtimeSessionRegistry.onlineUserIds(organizationId)).contains(stalledUserId);
        assertThat(realtimeSessionRegistry.holdsSubscription(sessionId, "stalled-sub")).isTrue();

        // The socket stays open on the client side and sends no heartbeat: a connection that
        // was dropped without a close frame looks the same to the server.
        Awaitility.await().atMost(WAIT).untilAsserted(() ->
                assertThat(realtimeSessionRegistry.onlineUserIds(organizationId)).doesNotContain(stalledUserId));

        Awaitility.await().atMost(WAIT).untilAsserted(() -> {
            SimpUser simpUser = simpUserRegistry.getUser(userKey);
            assertThat(simpUser == null || simpUser.getSessions().isEmpty()).isTrue();
        });
        assertThat(realtimeSessionRegistry.holdsSubscription(sessionId, "stalled-sub")).isFalse();
        realtimeSignalBus.publishToTopic(chatTopic(), TestSignal.of("sentinel"));
        watcher.awaitMark("sentinel");
        assertThat(watcher.presenceOfflineCount(stalledUserId)).isEqualTo(1);
    }

    @Test
    void silentConnection_afterDetection_reconnectsOnlyWithANewTicket() throws Exception {
        String usedTicket = issueTicket(stalledUserId);
        StalledSocket stalled = openStalledSocketWithTicket(usedTicket, "200,200");
        stalled.subscribe(chatTopic(), "stalled-sub");
        Awaitility.await().atMost(WAIT).untilAsserted(() ->
                assertThat(realtimeSessionRegistry.onlineUserIds(organizationId)).contains(stalledUserId));
        Awaitility.await().atMost(WAIT).untilAsserted(() ->
                assertThat(realtimeSessionRegistry.onlineUserIds(organizationId)).doesNotContain(stalledUserId));

        assertThatThrownBy(() -> openStalledSocketWithTicket(usedTicket, "0,0"))
                .hasMessageContaining("401");
        openStalledSocket(stalledUserId, "0,0");

        Awaitility.await().atMost(WAIT).untilAsserted(() ->
                assertThat(realtimeSessionRegistry.onlineUserIds(organizationId)).contains(stalledUserId));
    }

    @Test
    void slowConsumer_stopsReading_isClosedWhileASecondSubscriberReceivesEveryFrameInOrder() throws Exception {
        Watcher watcher = connectWatcher(watcherId);
        StalledSocket slow = openStalledSocket(stalledUserId, "0,0");
        slow.subscribe(chatTopic(), "slow-sub");
        awaitSubscriptions(stalledUserId + ":" + organizationId, 1);
        String padding = "x".repeat(FRAME_PADDING_CHARS);

        for (int index = 0; index < BURST_FRAMES; index++) {
            realtimeSignalBus.publishToTopic(chatTopic(), TestSignal.of(index + ":" + padding));
        }

        Awaitility.await().atMost(SLOW_CONSUMER_WAIT).untilAsserted(() ->
                assertThat(realtimeSessionRegistry.onlineUserIds(organizationId)).doesNotContain(stalledUserId));
        Awaitility.await().atMost(SLOW_CONSUMER_WAIT).untilAsserted(() ->
                assertThat(watcher.marks()).hasSize(BURST_FRAMES));
        List<Integer> indexes = watcher.marks().stream()
                .map((String mark) -> Integer.parseInt(mark.substring(0, mark.indexOf(':'))))
                .toList();
        assertThat(indexes).isSorted();
        assertThat(indexes).doesNotHaveDuplicates();
        assertThat(realtimeSessionRegistry.onlineUserIds(organizationId)).contains(watcherId);
    }

    private String chatTopic() {
        return RealtimeDestinationUtil.organizationTopic(organizationId, ChatConstant.CHAT_TOPIC_SUFFIX);
    }

    private void awaitSubscriptions(String userKey, int count) {
        Awaitility.await().atMost(WAIT).untilAsserted(() -> {
            SimpUser simpUser = simpUserRegistry.getUser(userKey);
            assertThat(simpUser).as("realtime user " + userKey).isNotNull();
            assertThat(simpUser.getSessions().stream().mapToInt((SimpSession s) -> s.getSubscriptions().size()).sum())
                    .isEqualTo(count);
        });
    }

    private String issueTicket(UUID user) {
        Jwt jwt = Jwt.withTokenValue("opaque")
                .header("alg", "none")
                .subject("kc-" + user)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3_600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        try {
            return realtimeTicketService.issueTicket(User.builder().id(user).build(), organizationId).ticket();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Watcher connectWatcher(UUID user) {
        if (stompClient == null) {
            stompClient = new WebSocketStompClient(new StandardWebSocketClient());
            stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        }
        StompHeaders headers = new StompHeaders();
        headers.setHeartbeat(new long[] {0, 0});
        Watcher watcher = new Watcher();
        watchers.add(watcher);
        stompClient.connectAsync("ws://localhost:" + port + "/ws?ticket=" + issueTicket(user),
                new WebSocketHttpHeaders(), headers, watcher);
        Awaitility.await().atMost(WAIT).untilAsserted(() -> assertThat(watcher.session).isNotNull());
        watcher.session.subscribe(chatTopic(), watcher.frameHandler());
        awaitSubscriptions(user + ":" + organizationId, 1);
        return watcher;
    }

    private StalledSocket openStalledSocket(UUID user, String heartbeat) throws IOException {
        return openStalledSocketWithTicket(issueTicket(user), heartbeat);
    }

    private StalledSocket openStalledSocketWithTicket(String ticket, String heartbeat) throws IOException {
        StalledSocket stalled = new StalledSocket();
        stalledSockets.add(stalled);
        stalled.open(port, ticket, heartbeat);
        return stalled;
    }

    /**
     * A WebSocket client written over a plain TCP socket whose receive buffer is tiny and which
     * is never read after CONNECTED: it neither answers heartbeats nor drains the frames the
     * server sends, and it never sends a close frame.
     */
    private static final class StalledSocket {

        private static final SecureRandom RANDOM = new SecureRandom();

        private Socket socket;
        private OutputStream out;

        void open(int port, String ticket, String heartbeat) throws IOException {
            socket = new Socket();
            socket.setReceiveBufferSize(1_024);
            socket.setSoTimeout((int) WAIT.toMillis());
            socket.connect(new InetSocketAddress("localhost", port));
            out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            byte[] key = new byte[16];
            RANDOM.nextBytes(key);
            out.write(("GET /ws?ticket=" + ticket + " HTTP/1.1\r\n"
                    + "Host: localhost:" + port + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + Base64.getEncoder().encodeToString(key) + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String response = readUntil(in, "\r\n\r\n");
            if (!response.startsWith("HTTP/1.1 101")) {
                throw new IOException("handshake refused: " + response.lines().findFirst().orElse(""));
            }
            sendFrame("CONNECT\naccept-version:1.2\nheart-beat:" + heartbeat + "\n\n");
            readUntil(in, "CONNECTED");
        }

        void subscribe(String destination, String subscriptionId) throws IOException {
            sendFrame("SUBSCRIBE\nid:" + subscriptionId + "\ndestination:" + destination + "\n\n");
        }

        void close() throws IOException {
            if (socket != null) {
                socket.close();
            }
        }

        private void sendFrame(String stompFrame) throws IOException {
            byte[] payload = (stompFrame + "\0").getBytes(StandardCharsets.UTF_8);
            byte[] mask = new byte[4];
            RANDOM.nextBytes(mask);
            out.write(0x81);
            out.write(0x80 | payload.length);
            out.write(mask);
            for (int index = 0; index < payload.length; index++) {
                out.write(payload[index] ^ mask[index % 4]);
            }
            out.flush();
        }

        private static String readUntil(InputStream in, String marker) throws IOException {
            StringBuilder read = new StringBuilder();
            while (!read.toString().contains(marker)) {
                int next = in.read();
                if (next < 0) {
                    throw new IOException("connection closed before " + marker);
                }
                read.append((char) next);
            }
            return read.toString();
        }
    }

    /**
     * A well-behaved STOMP client subscribed to the chat topic; records the signals it receives.
     */
    private static final class Watcher extends StompSessionHandlerAdapter {

        private volatile StompSession session;
        private final List<Map<String, Object>> signals = new CopyOnWriteArrayList<>();

        @Override
        public void afterConnected(StompSession connected, StompHeaders connectedHeaders) {
            this.session = connected;
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
                    signals.add((Map<String, Object>) payload);
                }
            };
        }

        List<String> marks() {
            return signals.stream()
                    .filter((Map<String, Object> signal) -> "TEST".equals(signal.get("type")))
                    .map((Map<String, Object> signal) -> String.valueOf(signal.get("mark")))
                    .toList();
        }

        void awaitMark(String mark) {
            Awaitility.await().atMost(WAIT).untilAsserted(() -> assertThat(marks()).contains(mark));
        }

        long presenceOfflineCount(UUID userId) {
            return signals.stream()
                    .filter((Map<String, Object> signal) -> "PRESENCE".equals(signal.get("type"))
                            && userId.toString().equals(signal.get("userId"))
                            && Boolean.FALSE.equals(signal.get("online")))
                    .count();
        }

        void disconnect() {
            StompSession current = session;
            if (current != null && current.isConnected()) {
                current.disconnect();
            }
        }
    }
}
