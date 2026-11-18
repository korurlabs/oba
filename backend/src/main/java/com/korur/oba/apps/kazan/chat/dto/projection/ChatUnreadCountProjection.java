package com.korur.oba.apps.kazan.chat.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * Unread count of one user in one scope: a channel when threadId is null,
 * else one thread the user is a member of.
 */
public interface ChatUnreadCountProjection {

    UUID getUserId();

    /**
     * Channel of the count; for a thread the thread's channel.
     */
    UUID getChannelId();

    /**
     * Thread of the count; null for the channel.
     */
    UUID getThreadId();

    /**
     * Unread messages, at most 100.
     */
    Integer getUnread();

    /**
     * Unread messages that notify the user, at most 100: a mention of the
     * user, directly or through here, an everyone mention, or a reply to a
     * live message of the user.
     */
    Integer getMentionCount();

    /**
     * Last read message of the scope's read state; null without read state
     * or when that message row is gone.
     */
    UUID getLastReadMessageId();

    /**
     * Read position in time; null when the user has no read state in the
     * scope yet and unread counts from the join time.
     */
    Instant getLastReadAt();
}
