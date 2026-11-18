package com.korur.oba.apps.kaya.lesson.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

@Schema(description = "A lesson a checkout reads at session start, without its text")
public record LessonDeliveryResponse(
        @Schema(description = "Lesson id", example = "1b2c3d4e-5f6a-4b7c-8d9e-0f1a2b3c4d5e")
        UUID id,

        @Schema(description = "The rule as a sentence", example = "Run the migration before the entity change")
        String title,

        @Schema(description = "The repository's name; null for a lesson of the whole organization", nullable = true,
                example = "village")
        String repository,

        @Schema(description = "Whether the lesson was created or updated after since; false without since", example = "true")
        boolean changed,

        @Schema(description = "When the lesson was last changed", example = "2026-10-25T09:00:00Z")
        Instant updatedAt
) {
}
