package com.korur.oba.apps.kazan.ticket.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * One list root of a sprint with what the burndown needs: its status and the moment it
 * reached DONE. The status is read as text.
 */
public interface SprintListRootProjection {

    UUID getId();

    String getStatus();

    Instant getCompletedAt();
}
