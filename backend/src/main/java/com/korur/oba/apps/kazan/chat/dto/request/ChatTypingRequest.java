package com.korur.oba.apps.kazan.chat.dto.request;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Body of a typing frame, sent to /app/chat/typing over STOMP. A null
 * threadId means the channel itself.
 *
 * @param channelId the channel typed in, or the channel of the thread
 * @param threadId  the thread typed in; null for the channel itself
 */
public record ChatTypingRequest(UUID channelId, UUID threadId) {
}
