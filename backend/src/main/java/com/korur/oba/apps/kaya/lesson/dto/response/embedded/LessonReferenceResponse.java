package com.korur.oba.apps.kaya.lesson.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

@Schema(description = "A repository or user a lesson names")
public record LessonReferenceResponse(
        @Schema(description = "Id", example = "3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b")
        UUID id,

        @Schema(description = "Display name; null for a user who left the organization", nullable = true, example = "village")
        String name
) {
}
