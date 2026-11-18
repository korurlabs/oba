package com.korur.oba.apps.kaya.session.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;

// 4. Internal

@Schema(description = "A subagent a session started")
public record SessionSubagentResponse(
        @Schema(description = "Claude's id of the subagent, not a Kaya agent", example = "a1b2c3")
        String agentId,

        @Schema(description = "Subagent type", example = "coding-engineer")
        String subagentType,

        @Schema(description = "Model the subagent ran on; null when not reported", nullable = true)
        String model,

        @Schema(description = "Short description of its task", nullable = true)
        String description,

        @Schema(description = "When it started")
        Instant startedAt,

        @Schema(description = "When it stopped; null while it runs", nullable = true)
        Instant endedAt
) {
}
