package com.korur.oba.apps.kaya.plan.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.plan.dto.response.embedded.PlanReferenceResponse;
import com.korur.oba.apps.kaya.plan.enums.PlanKind;
import com.korur.oba.apps.kaya.plan.enums.PlanReviewState;

@Schema(description = "A plan in the plans list")
public record PlanListResponse(
        @Schema(description = "Plan id")
        UUID id,

        @Schema(description = "SPEC or SESSION", example = "SPEC")
        PlanKind kind,

        @Schema(description = "First heading of a spec or Goal line of a session plan", example = "04 Kaya plans")
        String title,

        @Schema(description = "Repository-relative path of a spec; null for a session plan", nullable = true, example = "docs/private/plans/kaya/04_KAYA_PLANS.md")
        String path,

        @Schema(description = "The repository")
        PlanReferenceResponse repository,

        @Schema(description = "The pack the spec is about", nullable = true)
        PlanReferenceResponse pack,

        @Schema(description = "The user of the latest version")
        PlanReferenceResponse user,

        @Schema(description = "OPEN, APPROVED or CHANGES_REQUESTED", example = "OPEN")
        PlanReviewState reviewState,

        @Schema(description = "Number of the latest version", example = "2")
        int latestVersionNumber,

        @Schema(description = "Feedback not yet delivered to a session", example = "1")
        int openFeedbackCount,

        @Schema(description = "When the latest version was captured", example = "2026-10-25T09:00:00Z")
        Instant lastChangedAt
) {
}
