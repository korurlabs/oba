package com.korur.oba.apps.kaya.plan.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

@Schema(description = "The session that wrote the latest version of a plan")
public record PlanSessionResponse(
        @Schema(description = "Claude Code session id", example = "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
        String sessionId,

        @Schema(description = "Title of the session", example = "Plan the Kaya plans feature")
        String title,

        @Schema(description = "Whether the session has ended", example = "false")
        boolean ended,

        @Schema(description = "Git branch of the session", nullable = true, example = "convention-impl-kaya")
        String branch
) {
}
