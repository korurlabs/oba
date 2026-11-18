package com.korur.oba.apps.kaizen.chat.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * The reactions of one emoji on one message.
 */
public interface ChatReactionSummaryProjection {

    UUID getMessageId();

    String getEmoji();

    Integer getReactionCount();

    /**
     * True when the reader is one of the users who reacted with this emoji.
     */
    Boolean getReactedByMe();

    /**
     * The first 10 users who reacted with this emoji, in reaction order.
     */
    UUID[] getUserIds();

    /**
     * Time of the first reaction with this emoji, which orders the emoji of a
     * message.
     */
    Instant getFirstReactedAt();
}
