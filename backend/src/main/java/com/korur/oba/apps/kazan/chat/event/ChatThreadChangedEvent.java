package com.korur.oba.apps.kazan.chat.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * Published when a chat thread was created or changed, or when users joined
 * or left it.
 *
 * Consumers act after the transaction commits, so a write that rolls back
 * signals nothing. CREATED and UPDATED concern every member of the
 * organization; MEMBERS_JOINED and MEMBER_LEFT concern only the users named in
 * userIds, whose unread count and thread list change with their membership.
 *
 * @param type           what happened to the thread
 * @param organizationId organization whose channel holds the thread
 * @param channelId      channel of the thread
 * @param threadId       the thread
 * @param rootMessageId  channel message the thread was started from
 * @param userIds        users who joined or left; for CREATED the first members; empty for UPDATED
 */
public record ChatThreadChangedEvent(Type type,
                                     UUID organizationId,
                                     UUID channelId,
                                     UUID threadId,
                                     UUID rootMessageId,
                                     List<UUID> userIds) {

    /**
     * What happened to a thread.
     */
    public enum Type {
        CREATED,
        UPDATED,
        MEMBERS_JOINED,
        MEMBER_LEFT
    }
}
