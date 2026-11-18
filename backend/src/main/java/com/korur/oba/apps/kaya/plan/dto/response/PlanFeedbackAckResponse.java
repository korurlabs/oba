package com.korur.oba.apps.kaya.plan.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

@Schema(description = "The outcome of an acknowledgement of plan feedback")
public record PlanFeedbackAckResponse(
        @Schema(description = "Claude Code session id the feedback was delivered to",
                example = "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
        String sessionId,

        @Schema(description = "How many feedback rows were marked delivered now; rows already delivered and rows "
                + "outside the token's user are skipped", example = "2")
        int acknowledged
) {
}
