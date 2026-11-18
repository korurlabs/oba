package com.korur.oba.apps.kaya.plan.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.plan.constant.PlanConstant;
import com.korur.oba.apps.kaya.plan.enums.PlanFeedbackDecision;

/**
 * A reviewer's feedback on one version of a plan. The rules that span fields (a comment unless
 * APPROVE, both line bounds or neither, a range within the version) are checked by the service,
 * which answers them with KAY_PLN_002.
 */
@Schema(description = "Feedback on one version of a plan, optionally anchored to a line range")
public record PlanFeedbackRequest(
        @Schema(description = "The version the feedback is given on; APPROVE and REQUEST_CHANGES need the latest",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
        @NotNull(message = PlanConstant.VERSION_NUMBER_REQUIRED_MESSAGE)
        @Min(value = 1, message = PlanConstant.VERSION_NUMBER_MIN_MESSAGE)
        Integer versionNumber,

        @Schema(description = "APPROVE, REQUEST_CHANGES or COMMENT", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "REQUEST_CHANGES")
        @NotNull(message = PlanConstant.DECISION_REQUIRED_MESSAGE)
        PlanFeedbackDecision decision,

        @Schema(description = "The reviewer's words; required unless APPROVE", nullable = true,
                example = "Split step 3 into the migration and the entity change")
        @Size(min = 1, max = PlanConstant.COMMENT_MAX_LENGTH, message = PlanConstant.COMMENT_SIZE_MESSAGE)
        String comment,

        @Schema(description = "First line of the range, 1-based; set with lineEnd", nullable = true, example = "40")
        @Min(value = 1, message = PlanConstant.LINE_MIN_MESSAGE)
        Integer lineStart,

        @Schema(description = "Last line of the range, at most the version's line count; set with lineStart",
                nullable = true, example = "44")
        @Min(value = 1, message = PlanConstant.LINE_MIN_MESSAGE)
        Integer lineEnd,

        @Schema(description = "The selected text, at most 500 characters", nullable = true,
                example = "3. Add the plan entities and the migration")
        @Size(max = PlanConstant.QUOTE_MAX_LENGTH, message = PlanConstant.QUOTE_SIZE_MESSAGE)
        String quote
) {
}
