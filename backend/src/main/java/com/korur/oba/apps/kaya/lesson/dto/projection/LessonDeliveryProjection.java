package com.korur.oba.apps.kaya.lesson.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * What delivery reads of a lesson at session start: everything but its body.
 */
public interface LessonDeliveryProjection {

    UUID getId();

    String getTitle();

    UUID getRepositoryId();

    Instant getUpdatedAt();
}
