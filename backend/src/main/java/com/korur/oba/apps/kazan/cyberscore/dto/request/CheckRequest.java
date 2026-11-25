package com.korur.oba.apps.kazan.cyberscore.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.constant.CheckConstant;
import com.korur.oba.apps.kazan.cyberscore.enums.CheckCategory;
import com.korur.oba.apps.kazan.cyberscore.enums.CheckOutcome;
import com.korur.oba.apps.kazan.cyberscore.enums.CheckStatus;

/**
 * Request payload to create a check or to replace its fields.
 *
 * On create a missing outcome is TODO; on edit it keeps the current one. A
 * missing status is derived from the outcome on create and when the outcome
 * changes, and kept otherwise.
 */
@Schema(description = "Check create or update request")
public record CheckRequest(

        // Unique within the organization.
        @Schema(description = "Check name, unique in the organization", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Multi-factor sign-in for every account")
        @NotBlank(message = CheckConstant.NAME_REQUIRED_MESSAGE)
        @Size(max = CheckConstant.NAME_MAX_LENGTH, message = CheckConstant.NAME_SIZE_MESSAGE)
        String name,

        @Schema(description = "What the check covers", nullable = true,
                example = "Every user and admin account signs in with a second factor.")
        @Size(max = CheckConstant.TEXT_MAX_LENGTH, message = CheckConstant.DESCRIPTION_SIZE_MESSAGE)
        String description,

        // What a passing answer looks like.
        @Schema(description = "Expected state of a passing check", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "No account can sign in with a password alone.")
        @NotBlank(message = CheckConstant.EXPECTED_REQUIRED_MESSAGE)
        @Size(max = CheckConstant.TEXT_MAX_LENGTH, message = CheckConstant.EXPECTED_SIZE_MESSAGE)
        String expected,

        // Share of the check in the score.
        @Schema(description = "Weight in the score, 1 to 100", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "10")
        @NotNull(message = CheckConstant.WEIGHT_REQUIRED_MESSAGE)
        @Min(value = CheckConstant.MIN_WEIGHT, message = CheckConstant.WEIGHT_RANGE_MESSAGE)
        @Max(value = CheckConstant.MAX_WEIGHT, message = CheckConstant.WEIGHT_RANGE_MESSAGE)
        Integer weight,

        // Position within the category; null means 0.
        @Schema(description = "Position within the category, 0 to 9999; null means 0", nullable = true,
                example = "1")
        @Min(value = CheckConstant.MIN_SORT_ORDER, message = CheckConstant.SORT_ORDER_RANGE_MESSAGE)
        @Max(value = CheckConstant.MAX_SORT_ORDER, message = CheckConstant.SORT_ORDER_RANGE_MESSAGE)
        Integer sortOrder,

        // Null for an uncategorized check, which counts only towards the overall score.
        @Schema(description = "Category; null for an uncategorized check", nullable = true,
                example = "MICROSOFT_365")
        CheckCategory category,

        @Schema(description = "Current outcome; null means TODO on create and no change on edit", nullable = true,
                example = "PASS")
        CheckOutcome outcome,

        @Schema(description = "Workflow status; null derives it from the outcome on create or when the outcome "
                + "changes, and keeps it otherwise", nullable = true, example = "DONE")
        CheckStatus status
) {
}
