package com.korur.oba.apps.kaizen.chat.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * Published when a chat message was created, edited, deleted, pinned or
 * unpinned, in the channel or in a thread.
 *
 * Consumers act after the transaction commits, so a write that rolls back
 * signals nothing. The event holds IDs only. For a created or edited message,
 * {@link com.korur.oba.apps.kaizen.chat.listener.ChatRealtimeListener} reads
 * the message again and sends its reader-neutral copy on the channel topic,
 * which only the channel's readers may subscribe to; when that copy would
 * differ per reader, each client reads the message for itself.
 *
 * @param type           what happened to the message
 * @param organizationId organization whose chat holds the message
 * @param channelId      channel of the message, for a thread message the thread's channel
 * @param messageId      the message
 * @param threadId       thread of the message; null for a channel message
 * @param authorId       author of the message
 * @param createdAt      creation time of the message, its place in the conversation
 * @param clientNonce    nonce of the send that created the message, so the sender replaces its
 *                       pending row; null for every type but {@link Type#CREATED}; a daily update
 *                       entry carries the ID of its daily update
 */
public record ChatMessageChangedEvent(Type type,
                                      UUID organizationId,
                                      UUID channelId,
                                      UUID messageId,
                                      UUID threadId,
                                      UUID authorId,
                                      Instant createdAt,
                                      String clientNonce) {

    /**
     * What happened to a message. PINNED and UNPINNED change only the pin;
     * DELETED also stands for the pin a deleted message loses.
     */
    public enum Type {
        CREATED,
        UPDATED,
        DELETED,
        PINNED,
        UNPINNED
    }
}
