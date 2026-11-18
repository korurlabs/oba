package com.korur.oba.apps.kaizen.chat.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Published when a user's read position in the channel or in a thread moved
 * forward.
 *
 * Consumers act after the transaction commits. The user's other tabs and
 * devices refresh their unread badge from it, so reading on one device clears
 * the badge on all of them.
 *
 * @param organizationId    organization of the channel or thread
 * @param channelId         the channel read, or the channel of the thread
 * @param userId            user who read
 * @param threadId          the thread read; null for the channel
 * @param lastReadMessageId newest message the user has read
 */
public record ChatReadStateChangedEvent(UUID organizationId,
                                        UUID channelId,
                                        UUID userId,
                                        UUID threadId,
                                        UUID lastReadMessageId) {
}
