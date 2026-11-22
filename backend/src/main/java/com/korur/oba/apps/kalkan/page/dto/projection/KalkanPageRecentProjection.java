package com.korur.oba.apps.kalkan.page.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * One live page of the recent changes, with its path and the slug of its root.
 */
public interface KalkanPageRecentProjection {

    UUID getId();

    String getPath();

    String getRootSlug();

    Instant getUpdatedAt();

    UUID getUpdatedById();
}
