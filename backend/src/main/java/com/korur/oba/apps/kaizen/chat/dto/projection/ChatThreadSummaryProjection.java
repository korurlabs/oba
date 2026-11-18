package com.korur.oba.apps.kaizen.chat.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * What a channel message shows of the thread started from it: title,
 * activity and a few member avatars.
 */
public interface ChatThreadSummaryProjection {

    UUID getThreadId();

    UUID getRootMessageId();

    String getTitle();

    Integer getMessageCount();

    Instant getLastMessageAt();

    Integer getAutoArchiveMinutes();

    /**
     * Effective status: ACTIVE, CLOSED or ARCHIVED, the last also for an
     * ACTIVE thread quiet for its auto-archive time.
     */
    String getStatus();

    UUID getCreatedBy();

    Integer getMemberCount();

    /**
     * Up to 3 current thread members to show as avatars: the most recent
     * repliers first, then the thread's creator, then the others by join time.
     */
    UUID[] getMemberPreviewIds();
}
