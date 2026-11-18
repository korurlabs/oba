package com.korur.oba.apps.kaya.lesson.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.lesson.dto.response.embedded.LessonReferenceResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.embedded.LessonSessionResponse;

@Schema(description = "A lesson in the lessons list")
public record LessonListResponse(
        @Schema(description = "Lesson id", example = "1b2c3d4e-5f6a-4b7c-8d9e-0f1a2b3c4d5e")
        UUID id,

        @Schema(description = "The rule as a sentence", example = "Run the migration before the entity change")
        String title,

        @Schema(description = "What went wrong, why, and the correct approach, as markdown",
                example = "The context failed schema validation because the entity landed first.")
        String body,

        @Schema(description = "The repository; null for a lesson of the whole organization", nullable = true)
        LessonReferenceResponse repository,

        @Schema(description = "The author")
        LessonReferenceResponse user,

        @Schema(description = "The Claude Code session that recorded the lesson; Kaya may not know the session",
                nullable = true, example = "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
        String claudeSessionId,

        @Schema(description = "When the lesson was created", example = "2026-10-25T09:00:00Z")
        Instant createdAt,

        @Schema(description = "When the lesson was last changed", example = "2026-10-25T09:00:00Z")
        Instant updatedAt,

        @Schema(description = "Whether the signed-in user may edit and delete: the author, an ADMIN or an OWNER", example = "true")
        boolean canEdit,

        @Schema(description = "The session that recorded the lesson; null when none did or Kaya does not know it", nullable = true)
        LessonSessionResponse session
) {
}
