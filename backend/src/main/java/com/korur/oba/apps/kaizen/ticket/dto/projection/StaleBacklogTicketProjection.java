package com.korur.oba.apps.kaizen.ticket.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * One stale backlog ticket with its board and its last activity: the later of its last
 * edit and its newest live comment. The enum columns are read as text.
 */
public interface StaleBacklogTicketProjection {

    UUID getId();

    String getKey();

    String getTitle();

    String getStatus();

    String getPriority();

    String getMissionRank();

    UUID getBoardId();

    String getBoardName();

    String getBoardSlug();

    Instant getLastActivityAt();
}
