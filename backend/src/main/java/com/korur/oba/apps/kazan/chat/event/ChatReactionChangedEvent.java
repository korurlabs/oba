package com.korur.oba.apps.kazan.chat.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Published when a user added or removed an emoji reaction on a chat message.
 *
 * Consumers act after the transaction commits. Only a real change is
 * published: adding a reaction that exists or removing one that does not
 * publishes nothing.
 *
 * @param organizationId organization whose chat holds the message
 * @param channelId      channel of the message
 * @param messageId      the message reacted to
 * @param threadId       thread of the message; null for a channel message
 * @param emoji          the emoji
 * @param userId         user who reacted
 * @param added          true when the reaction was added, false when it was removed
 */
public record ChatReactionChangedEvent(UUID organizationId,
                                       UUID channelId,
                                       UUID messageId,
                                       UUID threadId,
                                       String emoji,
                                       UUID userId,
                                       boolean added) {
}
