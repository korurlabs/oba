package com.korur.oba.apps.kazan.cyberscore.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.constant.CyberscoreCheckConstant;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckOutcome;

/**
 * Request payload to record a check's current outcome. The status follows from
 * the outcome.
 */
@Schema(description = "Check outcome request")
public record CyberscoreCheckOutcomeRequest(

        @Schema(description = "Current outcome", requiredMode = Schema.RequiredMode.REQUIRED, example = "PARTIAL")
        @NotNull(message = CyberscoreCheckConstant.OUTCOME_REQUIRED_MESSAGE)
        CyberscoreCheckOutcome outcome
) {
}
