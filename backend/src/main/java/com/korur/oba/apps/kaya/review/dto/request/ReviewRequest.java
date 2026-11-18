package com.korur.oba.apps.kaya.review.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.review.constant.ReviewConstant;
import com.korur.oba.apps.kaya.review.enums.ReviewDecision;

/**
 * A reviewer's decision on a change, with the comment a rejection or a comment needs.
 */
@Schema(description = "A reviewer's decision or comment on a version in review")
public record ReviewRequest(
        @Schema(description = "APPROVE releases the version, REJECT sends it back to its proposer as a draft, "
                + "COMMENT leaves it in review", requiredMode = Schema.RequiredMode.REQUIRED, example = "APPROVE")
        @NotNull(message = ReviewConstant.DECISION_REQUIRED_MESSAGE)
        ReviewDecision decision,

        @Schema(description = "The reviewer's words; required for REJECT and COMMENT", nullable = true,
                example = "The new allow rule is too broad; limit it to git status")
        @Size(min = 1, max = ReviewConstant.COMMENT_MAX_LENGTH, message = ReviewConstant.COMMENT_SIZE_MESSAGE)
        String comment
) {
}
