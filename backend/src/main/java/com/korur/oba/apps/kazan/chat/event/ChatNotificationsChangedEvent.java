package com.korur.oba.apps.kazan.chat.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * Published when an edit changed whom a message notifies: users were
 * mentioned or no longer are, or the everyone mention was added or removed.
 *
 * A notification count is derived from the mentions of unread messages, so
 * such an edit changes the counts of the users concerned although no message
 * was created or deleted. Consumers act after the transaction commits and
 * push fresh counts: to everyone the conversation reaches when broadcast is
 * true, else to the users in userIds.
 *
 * @param organizationId organization of the message
 * @param channelId      channel of the message
 * @param threadId       thread of the message; null for the channel
 * @param messageId      the edited message
 * @param authorId       author of the message, whose own counts never change
 * @param userIds        users who gained or lost a mention, directly or through here
 * @param broadcast      true when the everyone mention was added or removed
 */
public record ChatNotificationsChangedEvent(UUID organizationId,
                                            UUID channelId,
                                            UUID threadId,
                                            UUID messageId,
                                            UUID authorId,
                                            List<UUID> userIds,
                                            boolean broadcast) {
}
