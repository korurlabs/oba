package com.korur.oba.apps.kaya.lesson.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

@Schema(description = "The session that recorded a lesson")
public record LessonSessionResponse(
        @Schema(description = "Session row id", example = "8a7b6c5d-4e3f-4a2b-9c1d-0e9f8a7b6c5d")
        UUID id,

        @Schema(description = "Claude Code session id", example = "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
        String sessionId,

        @Schema(description = "Title of the session", nullable = true, example = "Add the lessons API")
        String title
) {
}
