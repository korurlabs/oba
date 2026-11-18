package com.korur.oba.apps.kaya.plan.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.plan.dto.response.embedded.PlanReferenceResponse;
import com.korur.oba.apps.kaya.plan.enums.PlanFeedbackDecision;

@Schema(description = "A reviewer's feedback on a version of a plan, with its delivery")
public record PlanFeedbackResponse(
        @Schema(description = "Feedback id")
        UUID id,

        @Schema(description = "The version the feedback was given on", example = "1")
        int versionNumber,

        @Schema(description = "The reviewer, a user")
        PlanReferenceResponse reviewer,

        @Schema(description = "APPROVE, REQUEST_CHANGES or COMMENT", example = "REQUEST_CHANGES")
        PlanFeedbackDecision decision,

        @Schema(description = "The reviewer's words; null for an APPROVE without one", nullable = true, example = "Split step 3")
        String comment,

        @Schema(description = "First line of the range", nullable = true, example = "40")
        Integer lineStart,

        @Schema(description = "Last line of the range", nullable = true, example = "44")
        Integer lineEnd,

        @Schema(description = "The quoted text", nullable = true)
        String quote,

        @Schema(description = "When it was given", example = "2026-10-25T09:10:00Z")
        Instant createdAt,

        @Schema(description = "When a session received it; null while it waits", nullable = true)
        Instant deliveredAt,

        @Schema(description = "Claude Code session id of the session that received it", nullable = true)
        String deliveredSessionId
) {
}
