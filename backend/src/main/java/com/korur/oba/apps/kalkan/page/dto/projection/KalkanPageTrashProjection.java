package com.korur.oba.apps.kalkan.page.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * The top page of one delete, with the path it had and the number of pages the same delete took
 * below it.
 */
public interface KalkanPageTrashProjection {

    UUID getId();

    String getPath();

    Instant getDeletedAt();

    UUID getDeletedById();

    Long getDescendantCount();
}
