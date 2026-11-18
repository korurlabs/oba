package com.korur.oba.apps.kaya.review.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.review.enums.ReviewDecision;

/**
 * One recorded review of a change.
 */
@Schema(description = "One decision or comment of a reviewer on a version")
public record ReviewResponse(
        @Schema(description = "Review id", example = "7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d")
        UUID id,

        @Schema(description = "Version reviewed", example = "6b0e2f4a-1c3d-4e5f-8a9b-0c1d2e3f4a5b")
        UUID versionId,

        @Schema(description = "User who reviewed", example = "4e5f6a7b-8c9d-4e0f-a1b2-c3d4e5f6a7b8")
        UUID reviewerId,

        @Schema(description = "The decision", example = "REJECT")
        ReviewDecision decision,

        @Schema(description = "The reviewer's words", nullable = true, example = "Limit the rule to git status")
        String comment,

        @Schema(description = "When the review was recorded")
        Instant createdAt
) {
}
