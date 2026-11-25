package com.korur.oba.apps.kazan.cyberscore.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.constant.CyberscoreCheckConstant;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckStatus;

/**
 * Request payload to set a check's workflow status, which never affects the score.
 */
@Schema(description = "Check status request")
public record CyberscoreCheckStatusRequest(

        @Schema(description = "Workflow status", requiredMode = Schema.RequiredMode.REQUIRED, example = "IN_PROGRESS")
        @NotNull(message = CyberscoreCheckConstant.STATUS_REQUIRED_MESSAGE)
        CyberscoreCheckStatus status
) {
}
