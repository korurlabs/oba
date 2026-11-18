package com.korur.oba.apps.kaizen.alertroute.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * One row of the alert map with the board fields the Alerts tab shows, read
 * natively so that a route whose board was soft-deleted is still listed.
 */
public interface AlertRouteListProjection {

    UUID getId();

    String getSystemKey();

    Boolean getIsDefault();

    Instant getUpdatedAt();

    UUID getBoardId();

    String getBoardName();

    String getBoardPrefix();

    String getBoardColor();
}
