package com.korur.oba.apps.kazan.dailyupdate.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * Creation time of an author's first live update within a time window.
 */
public interface DailyUpdateFirstPostProjection {

    UUID getAuthorId();

    Instant getFirstPostedAt();
}
