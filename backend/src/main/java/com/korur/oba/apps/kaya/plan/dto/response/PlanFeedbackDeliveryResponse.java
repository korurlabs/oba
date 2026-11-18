package com.korur.oba.apps.kaya.plan.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.plan.enums.PlanFeedbackDecision;
import com.korur.oba.apps.kaya.plan.enums.PlanKind;

@Schema(description = "A reviewer's feedback that waits for delivery to a session of the plan's user")
public record PlanFeedbackDeliveryResponse(
        @Schema(description = "Feedback id, sent back in the acknowledgement")
        UUID id,

        @Schema(description = "The plan")
        UUID planId,

        @Schema(description = "The plan's page in the Kaya web app", example = "https://kaya.korur.com/plans/2b7e1c4d-5f6a-4b7c-8d9e-0f1a2b3c4d5e")
        String planUrl,

        @Schema(description = "SPEC or SESSION", example = "SPEC")
        PlanKind kind,

        @Schema(description = "Repository-relative path of a SPEC plan; null for a SESSION plan", nullable = true,
                example = "docs/private/plans/kaya/04_KAYA_PLANS.md")
        String path,

        @Schema(description = "The version the feedback was given on", example = "1")
        int versionNumber,

        @Schema(description = "The plan's latest version", example = "2")
        int latestVersionNumber,

        @Schema(description = "Claude Code session id of the session that wrote the latest version", nullable = true)
        String sessionId,

        @Schema(description = "The reviewer's display name", example = "Ada Lovelace")
        String reviewerName,

        @Schema(description = "APPROVE, REQUEST_CHANGES or COMMENT", example = "REQUEST_CHANGES")
        PlanFeedbackDecision decision,

        @Schema(description = "The reviewer's words; null for an APPROVE without one", nullable = true)
        String comment,

        @Schema(description = "First line of the range", nullable = true, example = "40")
        Integer lineStart,

        @Schema(description = "Last line of the range", nullable = true, example = "44")
        Integer lineEnd,

        @Schema(description = "The quoted text", nullable = true)
        String quote,

        @Schema(description = "When it was given", example = "2026-10-25T09:10:00Z")
        Instant createdAt
) {
}
