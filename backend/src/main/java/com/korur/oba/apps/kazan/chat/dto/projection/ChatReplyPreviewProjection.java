package com.korur.oba.apps.kazan.chat.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The part of a replied-to message that a reply preview shows. Read
 * including soft-deleted rows, so a reply whose target was deleted still
 * says so instead of losing its quote.
 */
public interface ChatReplyPreviewProjection {

    UUID getId();

    UUID getAuthorId();

    /**
     * Message text with its tokens as stored; the caller renders and cuts it.
     */
    String getContent();

    /**
     * Daily update the target stands for; null for every other message.
     */
    UUID getDailyUpdateId();

    /**
     * Pull request share the target stands for; null for every other message.
     */
    UUID getPullRequestShareId();

    Boolean getDeleted();
}
