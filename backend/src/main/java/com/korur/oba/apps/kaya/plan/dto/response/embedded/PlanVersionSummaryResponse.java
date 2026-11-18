package com.korur.oba.apps.kaya.plan.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.apps.kaya.plan.enums.PlanVersionSource;

@Schema(description = "One version of a plan, without its content")
public record PlanVersionSummaryResponse(
        @Schema(description = "Version number, 1 for the first capture", example = "2")
        int number,

        @Schema(description = "How the content was captured: FILE, PLAN_MODE or MESSAGE", example = "FILE")
        PlanVersionSource source,

        @Schema(description = "Claude Code session id of the writing session; null when that session was deleted", nullable = true, example = "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
        String sessionId,

        @Schema(description = "When the content was captured", example = "2026-10-25T09:00:00Z")
        Instant capturedAt,

        @Schema(description = "Number of lines of the content", example = "297")
        int lineCount
) {
}
