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
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

// 2. Third-party
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.awaitility.Awaitility;
import static org.assertj.core.api.Assertions.assertThat;
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
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.connect.realtime.dto.RealtimeSignal;
import com.korur.oba.apps.kazan.chat.integration.RealtimeStompTestApplication.TestSignal;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.connect.realtime.service.RealtimeSignalBus;
import com.korur.oba.connect.realtime.service.RealtimeTicketService;
import com.korur.oba.connect.realtime.util.RealtimeDestinationUtil;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Channel topics through the real handshake and the real chat destination authorizer: who may
 * subscribe, who stops receiving when the right to read ends, and how large message frames
 * travel. The channel read check is the mocked {@link ChatChannelQueryService#canRead}, stubbed per
 * scenario, and the signals are published through the signal bus to the channel topic as the
 * chat listener does, so the slice needs no chat stack.
 *
 * The test clients accept frames of up to 1 MiB, because the default WebSocket text buffer of
 * the client (8 KiB) is smaller than a message frame.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = RealtimeStompTestApplication.class,
        properties = {"oba.realtime.expiry-sweep-ms=600000"})
class RealtimeStompChannelIT {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final Duration SLOW_CONSUMER_WAIT = Duration.ofSeconds(90);
    private static final int CLIENT_FRAME_LIMIT_BYTES = 1024 * 1024;
    private static final int MESSAGE_BODY_CHARS = 30_000;
    private static final int BURST_FRAMES = 600;

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
    private final UUID channelId = UUID.randomUUID();
    private final UUID readerId = UUID.randomUUID();
    private final UUID otherReaderId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();

    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final List<SlowSocket> slowSockets = new CopyOnWriteArrayList<>();
    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUpClient() {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(CLIENT_FRAME_LIMIT_BYTES);
        stompClient = new WebSocketStompClient(new StandardWebSocketClient(container));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompClient.setInboundMessageSizeLimit(CLIENT_FRAME_LIMIT_BYTES);

        when(chatChannelQueryService.canRead(any(UUID.class), any(UUID.class), any(UUID.class))).thenReturn(false);
        when(chatChannelQueryService.canRead(eq(organizationId), eq(readerId), eq(channelId))).thenReturn(true);
        when(chatChannelQueryService.canRead(eq(organizationId), eq(otherReaderId), eq(channelId))).thenReturn(true);
        when(appAccessQueryService.hasAppAccess(any(UUID.class), any(UUID.class), eq(App.KAZAN))).thenReturn(true);
    }

    @AfterEach
    void closeClients() throws IOException {
        for (Client client : clients) {
            client.disconnect();
        }
        for (SlowSocket slow : slowSockets) {
            slow.close();
        }
        stompClient.stop();
        SecurityContextHolder.clearContext();
    }

    @Test
    void subscribe_channelTopicAsReader_receivesTheChannelFrames() {
        Client reader = connect(readerId);

        reader.subscribe(channelTopic());
        awaitSubscriptions(readerId, 1);
        realtimeSignalBus.publishToTopic(channelTopic(), TestSignal.of("first"));
        realtimeSignalBus.publishToTopic(channelTopic(), TestSignal.of("second"));

        reader.awaitMark("second");
        assertThat(reader.marks()).containsExactly("first", "second");
        assertThat(reader.errors).isEmpty();
    }

    @Test
    void subscribe_channelTopicAsNonReader_isDroppedAndTheSocketStaysOpen() {
        Client outsider = connect(outsiderId);

        outsider.subscribe(channelTopic());
        outsider.subscribe(chatTopic());
        awaitSubscriptions(outsiderId, 1);
        realtimeSignalBus.publishToTopic(channelTopic(), TestSignal.of("channel-frame"));
        realtimeSignalBus.publishToTopic(chatTopic(), TestSignal.of("chat-sentinel"));

        outsider.awaitMark("chat-sentinel");
        assertThat(outsider.marks()).containsExactly("chat-sentinel");
        assertThat(outsider.errors).isEmpty();
        assertThat(outsider.session.isConnected()).isTrue();
    }

    @Test
    void revalidateSubscriptions_removedMember_stopsReceivingWhileTheRemainingMemberKeeps() {
        Client removed = connect(readerId);
        Client remaining = connect(otherReaderId);
        removed.subscribe(channelTopic());
        remaining.subscribe(channelTopic());
        awaitSubscriptions(readerId, 1);
        awaitSubscriptions(otherReaderId, 1);
        realtimeSignalBus.publishToTopic(channelTopic(), TestSignal.of("before"));
        removed.awaitMark("before");
        remaining.awaitMark("before");

        when(chatChannelQueryService.canRead(eq(organizationId), eq(readerId), eq(channelId))).thenReturn(false);
        int revoked = realtimeSessionRegistry.revalidateSubscriptions(List.of(readerId, otherReaderId));
        realtimeSignalBus.publishToTopic(channelTopic(), TestSignal.of("after"));

        assertThat(revoked).isEqualTo(1);
        remaining.awaitMark("after");
        assertThat(remaining.marks()).containsExactly("before", "after");
        assertThat(removed.marks()).containsExactly("before");
        assertThat(removed.errors).isEmpty();
        assertThat(removed.session.isConnected()).isTrue();
    }

    @Test
    void revalidateSubscriptions_channelSwitchedToPrivateForANonMember_stopsThatUserOnly() {
        Client formerReader = connect(outsiderId);
        Client member = connect(readerId);
        // Public channel: everyone may read it until the switch.
        when(chatChannelQueryService.canRead(eq(organizationId), eq(outsiderId), eq(channelId))).thenReturn(true);
        formerReader.subscribe(channelTopic());
        member.subscribe(channelTopic());
        awaitSubscriptions(outsiderId, 1);
        awaitSubscriptions(readerId, 1);
        realtimeSignalBus.publishToTopic(channelTopic(), TestSignal.of("public"));
        formerReader.awaitMark("public");
        member.awaitMark("public");

        when(chatChannelQueryService.canRead(eq(organizationId), eq(outsiderId), eq(channelId))).thenReturn(false);
        int revoked = realtimeSessionRegistry.revalidateSubscriptions(List.of(outsiderId));
        realtimeSignalBus.publishToTopic(channelTopic(), TestSignal.of("private"));

        assertThat(revoked).isEqualTo(1);
        member.awaitMark("private");
        assertThat(formerReader.marks()).containsExactly("public");
        assertThat(member.marks()).containsExactly("public", "private");
    }

    @Test
    void publishToTopic_messageCreatedFrameOf30Kb_isDeliveredToTheSubscriber() {
        Client reader = connect(readerId);
        reader.subscribe(channelTopic());
        awaitSubscriptions(readerId, 1);
        String body = "m".repeat(MESSAGE_BODY_CHARS);

        realtimeSignalBus.publishToTopic(channelTopic(), PayloadSignal.of(0, body));

        Awaitility.await().atMost(WAIT).untilAsserted(() -> assertThat(reader.payloads()).hasSize(1));
        Map<String, Object> message = reader.payloads().get(0);
        assertThat(message.get("body")).isEqualTo(body);
        assertThat(message.get("index")).isEqualTo(0);
        assertThat(reader.errors).isEmpty();
    }

    @Test
    void publishToTopic_slowConsumerOfPayloadFrames_isClosedWhileASecondSubscriberReceivesEveryFrameInOrder()
            throws Exception {
        Client reader = connect(readerId);
        reader.subscribe(channelTopic());
        awaitSubscriptions(readerId, 1);
        UUID slowUserId = outsiderId;
        when(chatChannelQueryService.canRead(eq(organizationId), eq(slowUserId), eq(channelId))).thenReturn(true);
        SlowSocket slow = openSlowSocket(slowUserId);
        slow.subscribe(channelTopic(), "slow-sub");
        awaitSubscriptions(slowUserId, 1);
        String body = "m".repeat(MESSAGE_BODY_CHARS);

        for (int index = 0; index < BURST_FRAMES; index++) {
            realtimeSignalBus.publishToTopic(channelTopic(), PayloadSignal.of(index, body));
        }

        Awaitility.await().atMost(SLOW_CONSUMER_WAIT).untilAsserted(() ->
                assertThat(realtimeSessionRegistry.onlineUserIds(organizationId)).doesNotContain(slowUserId));
        Awaitility.await().atMost(SLOW_CONSUMER_WAIT).untilAsserted(() ->
                assertThat(reader.payloads()).hasSize(BURST_FRAMES));
        List<Integer> indexes = reader.payloads().stream()
                .map((Map<String, Object> message) -> (Integer) message.get("index"))
                .toList();
        assertThat(indexes).isSorted();
        assertThat(indexes).doesNotHaveDuplicates();
        assertThat(realtimeSessionRegistry.onlineUserIds(organizationId)).contains(readerId);
        assertThat(reader.errors).isEmpty();
    }

    private String channelTopic() {
        return RealtimeDestinationUtil.organizationTopic(organizationId,
                ChatConstant.CHAT_CHANNEL_TOPIC_SUFFIX_PREFIX + channelId);
    }

    private String chatTopic() {
        return RealtimeDestinationUtil.organizationTopic(organizationId, ChatConstant.CHAT_TOPIC_SUFFIX);
    }

    private void awaitSubscriptions(UUID user, int count) {
        String userKey = user + ":" + organizationId;
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

    private Client connect(UUID user) {
        StompHeaders headers = new StompHeaders();
        headers.setHeartbeat(new long[] {0, 0});
        Client client = new Client();
        clients.add(client);
        stompClient.connectAsync("ws://localhost:" + port + "/ws?ticket=" + issueTicket(user),
                new WebSocketHttpHeaders(), headers, client);
        Awaitility.await().atMost(WAIT).untilAsserted(() -> {
            assertThat(client.errors).as("errors before CONNECTED").isEmpty();
            assertThat(client.session).as("session").isNotNull();
        });
        return client;
    }

    private SlowSocket openSlowSocket(UUID user) throws IOException {
        SlowSocket slow = new SlowSocket();
        slowSockets.add(slow);
        slow.open(port, issueTicket(user));
        return slow;
    }

    /**
     * The payload frame under test: a chat message signal carrying a large message body.
     *
     * @param type    always "MESSAGE_CREATED"
     * @param message the message with its position in the burst and a large body
     */
    public record PayloadSignal(String type, Map<String, Object> message) implements RealtimeSignal {

        static PayloadSignal of(int index, String body) {
            return new PayloadSignal("MESSAGE_CREATED", Map.of("index", index, "body", body));
        }
    }

    /**
     * A WebSocket client written over a plain TCP socket whose receive buffer is tiny and which
     * is never read after CONNECTED, so the server's outbound buffer for it only grows.
     */
    private static final class SlowSocket {

        private static final SecureRandom RANDOM = new SecureRandom();

        private Socket socket;
        private OutputStream out;

        void open(int port, String ticket) throws IOException {
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
            sendFrame("CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n");
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

        // Client frames are masked; the SUBSCRIBE frame is under 126 bytes of payload only for
        // short topics, so the extended 16-bit length is used.
        private void sendFrame(String stompFrame) throws IOException {
            byte[] payload = (stompFrame + "\0").getBytes(StandardCharsets.UTF_8);
            byte[] mask = new byte[4];
            RANDOM.nextBytes(mask);
            out.write(0x81);
            out.write(0x80 | 126);
            out.write(payload.length >> 8);
            out.write(payload.length & 0xFF);
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

        void subscribe(String destination) {
            session.subscribe(destination, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(StompHeaders headers, Object payload) {
                    signals.add((Map<String, Object>) payload);
                }
            });
        }

        List<String> marks() {
            return signals.stream()
                    .filter((Map<String, Object> signal) -> "TEST".equals(signal.get("type")))
                    .map((Map<String, Object> signal) -> String.valueOf(signal.get("mark")))
                    .toList();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> payloads() {
            return signals.stream()
                    .filter((Map<String, Object> signal) -> "MESSAGE_CREATED".equals(signal.get("type")))
                    .map((Map<String, Object> signal) -> (Map<String, Object>) signal.get("message"))
                    .toList();
        }

        void awaitMark(String mark) {
            Awaitility.await().atMost(WAIT).untilAsserted(() -> assertThat(marks()).contains(mark));
        }

        void disconnect() {
            StompSession current = session;
            if (current != null && current.isConnected()) {
                current.disconnect();
            }
        }
    }
}
