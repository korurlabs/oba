package com.korur.oba.apps.kaya.lesson.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

@Schema(description = "A lesson a search found, with its text")
public record LessonDeliverySearchResponse(
        @Schema(description = "Lesson id", example = "1b2c3d4e-5f6a-4b7c-8d9e-0f1a2b3c4d5e")
        UUID id,

        @Schema(description = "The rule as a sentence", example = "Run the migration before the entity change")
        String title,

        @Schema(description = "What went wrong, why, and the correct approach, as markdown",
                example = "The context failed schema validation because the entity landed first.")
        String body,

        @Schema(description = "The repository's name; null for a lesson of the whole organization", nullable = true,
                example = "village")
        String repository,

        @Schema(description = "When the lesson was last changed", example = "2026-10-25T09:00:00Z")
        Instant updatedAt
) {
}
