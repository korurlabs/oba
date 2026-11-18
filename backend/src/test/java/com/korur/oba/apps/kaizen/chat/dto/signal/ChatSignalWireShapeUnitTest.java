package com.korur.oba.apps.kaizen.chat.dto.signal;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// 2. Third-party
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.request.ChatTypingRequest;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatConversationUnreadResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatUnreadResponse;
import com.korur.oba.apps.kaizen.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kaizen.chat.event.ChatChannelChangedEvent;
import com.korur.oba.apps.kaizen.chat.event.ChatMessageChangedEvent;
import com.korur.oba.apps.kaizen.chat.event.ChatReactionChangedEvent;
import com.korur.oba.apps.kaizen.chat.event.ChatThreadChangedEvent;

/**
 * Pins the JSON the clients parse for every chat signal: field names, order
 * and value forms, as Spring Boot's message converter writes them.
 */
class ChatSignalWireShapeUnitTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THREAD_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final Instant CREATED_AT = Instant.parse("2026-10-20T09:15:30Z");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void messageSignal() throws JsonProcessingException {
        ChatMessageChangedEvent event = new ChatMessageChangedEvent(ChatMessageChangedEvent.Type.CREATED,
                ORGANIZATION_ID, CHANNEL_ID, MESSAGE_ID, null, USER_ID, CREATED_AT, "nonce-1");

        assertThat(objectMapper.writeValueAsString(ChatMessageSignal.of("MESSAGE_CREATED", event))).isEqualTo(
                "{\"type\":\"MESSAGE_CREATED\",\"messageId\":\"" + MESSAGE_ID + "\",\"threadId\":null,"
                        + "\"authorId\":\"" + USER_ID + "\",\"createdAt\":\"2026-10-20T09:15:30Z\","
                        + "\"clientNonce\":\"nonce-1\",\"version\":null,\"message\":null}");
    }

    @Test
    void messageSignal_withVersionAndMessage() throws JsonProcessingException {
        Instant version = Instant.parse("2026-10-20T09:20:00Z");
        ChatMessageChangedEvent event = new ChatMessageChangedEvent(ChatMessageChangedEvent.Type.UPDATED,
                ORGANIZATION_ID, CHANNEL_ID, MESSAGE_ID, null, USER_ID, CREATED_AT, null);
        ChatMessageResponse message = mock(ChatMessageResponse.class);

        ChatMessageSignal signal = ChatMessageSignal.of("MESSAGE_UPDATED", event, version, message);
        String json = objectMapper.writeValueAsString(signal);

        assertThat(signal.version()).isEqualTo(version);
        assertThat(json).contains("\"version\":\"2026-10-20T09:20:00Z\"");
        assertThat(json).startsWith("{\"type\":\"MESSAGE_UPDATED\",\"messageId\":\"" + MESSAGE_ID + "\"");
        assertThat(json.indexOf("\"version\"")).isLessThan(json.indexOf("\"message\""));
    }

    @Test
    void messageSignal_withVersionWithoutMessage() throws JsonProcessingException {
        Instant version = Instant.parse("2026-10-20T09:20:00Z");
        ChatMessageChangedEvent event = new ChatMessageChangedEvent(ChatMessageChangedEvent.Type.UPDATED,
                ORGANIZATION_ID, CHANNEL_ID, MESSAGE_ID, null, USER_ID, CREATED_AT, null);

        assertThat(objectMapper.writeValueAsString(ChatMessageSignal.of("MESSAGE_UPDATED", event, version, null)))
                .endsWith("\"version\":\"2026-10-20T09:20:00Z\",\"message\":null}");
    }

    @Test
    void pinSignal() throws JsonProcessingException {
        ChatMessageChangedEvent event = new ChatMessageChangedEvent(ChatMessageChangedEvent.Type.PINNED,
                ORGANIZATION_ID, CHANNEL_ID, MESSAGE_ID, THREAD_ID, USER_ID, CREATED_AT, null);

        assertThat(objectMapper.writeValueAsString(ChatPinSignal.of(event, true))).isEqualTo(
                "{\"type\":\"PIN_CHANGED\",\"messageId\":\"" + MESSAGE_ID + "\",\"threadId\":\"" + THREAD_ID
                        + "\",\"pinned\":true}");
    }

    @Test
    void threadSignal() throws JsonProcessingException {
        ChatThreadChangedEvent event = new ChatThreadChangedEvent(ChatThreadChangedEvent.Type.CREATED,
                ORGANIZATION_ID, CHANNEL_ID, THREAD_ID, MESSAGE_ID, List.of(USER_ID));

        assertThat(objectMapper.writeValueAsString(ChatThreadSignal.of("THREAD_CREATED", event))).isEqualTo(
                "{\"type\":\"THREAD_CREATED\",\"threadId\":\"" + THREAD_ID + "\",\"rootMessageId\":\""
                        + MESSAGE_ID + "\"}");
    }

    @Test
    void reactionSignal() throws JsonProcessingException {
        ChatReactionChangedEvent event = new ChatReactionChangedEvent(ORGANIZATION_ID, CHANNEL_ID, MESSAGE_ID, null,
                "👍", USER_ID, false);

        assertThat(objectMapper.writeValueAsString(ChatReactionSignal.of(event))).isEqualTo(
                "{\"type\":\"REACTION_CHANGED\",\"messageId\":\"" + MESSAGE_ID + "\",\"threadId\":null,"
                        + "\"emoji\":\"👍\",\"userId\":\"" + USER_ID + "\",\"added\":false}");
    }

    @Test
    void unreadSignal() throws JsonProcessingException {
        ChatUnreadResponse unread = new ChatUnreadResponse(
                List.of(new ChatConversationUnreadResponse(CHANNEL_ID, CHANNEL_ID, 3, 1, MESSAGE_ID, CREATED_AT)),
                List.of(new ChatConversationUnreadResponse(THREAD_ID, CHANNEL_ID, 2, 1, null, null)), 5, 2);

        assertThat(objectMapper.writeValueAsString(ChatUnreadSignal.of(unread))).isEqualTo(
                "{\"type\":\"UNREAD_CHANGED\",\"unread\":{\"channels\":[{\"id\":\"" + CHANNEL_ID
                        + "\",\"channelId\":\"" + CHANNEL_ID + "\",\"unread\":3,\"mentionCount\":1,"
                        + "\"lastReadMessageId\":\"" + MESSAGE_ID + "\",\"lastReadAt\":\"2026-10-20T09:15:30Z\"}],"
                        + "\"threads\":[{\"id\":\"" + THREAD_ID + "\",\"channelId\":\"" + CHANNEL_ID
                        + "\",\"unread\":2,\"mentionCount\":1,\"lastReadMessageId\":null,\"lastReadAt\":null}],"
                        + "\"total\":5,\"mentionTotal\":2}}");
    }

    @Test
    void channelSignal() throws JsonProcessingException {
        ChatChannelChangedEvent event = new ChatChannelChangedEvent(ChatChannelChangedEvent.Type.UPDATED,
                ORGANIZATION_ID, CHANNEL_ID, ChatChannelVisibility.PUBLIC, List.of());

        assertThat(objectMapper.writeValueAsString(ChatChannelSignal.of(event))).isEqualTo(
                "{\"type\":\"CHANNEL_CHANGED\",\"channelId\":\"" + CHANNEL_ID + "\"}");
    }

    @Test
    void threadMembershipSignal() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(ChatThreadMembershipSignal.of(THREAD_ID, true))).isEqualTo(
                "{\"type\":\"THREAD_MEMBERSHIP_CHANGED\",\"threadId\":\"" + THREAD_ID + "\",\"member\":true}");
    }

    @Test
    void typingSignal() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(ChatTypingSignal.of(USER_ID, null))).isEqualTo(
                "{\"type\":\"TYPING\",\"userId\":\"" + USER_ID + "\",\"threadId\":null}");
        assertThat(objectMapper.writeValueAsString(ChatTypingSignal.of(USER_ID, THREAD_ID))).isEqualTo(
                "{\"type\":\"TYPING\",\"userId\":\"" + USER_ID + "\",\"threadId\":\"" + THREAD_ID + "\"}");
    }

    @Test
    void presenceSignal() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(ChatPresenceSignal.of(USER_ID, true))).isEqualTo(
                "{\"type\":\"PRESENCE\",\"userId\":\"" + USER_ID + "\",\"online\":true}");
    }

    @Test
    void typingRequest() throws JsonProcessingException {
        assertThat(objectMapper.readValue("{\"channelId\":\"" + CHANNEL_ID + "\",\"threadId\":\"" + THREAD_ID
                + "\"}", ChatTypingRequest.class)).isEqualTo(new ChatTypingRequest(CHANNEL_ID, THREAD_ID));
        assertThat(objectMapper.readValue("{\"channelId\":\"" + CHANNEL_ID + "\",\"threadId\":null}",
                ChatTypingRequest.class)).isEqualTo(new ChatTypingRequest(CHANNEL_ID, null));
    }
}
