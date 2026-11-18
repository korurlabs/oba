package com.korur.oba.connect.alertmanager.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.time.Instant;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.alertmanager.constant.AlertmanagerConstant;

/**
 * One alert of an Alertmanager webhook notification (payload version 4).
 */
@Schema(description = "Alertmanager alert")
public record AlertmanagerAlertRequest(

        // firing or resolved.
        @Schema(description = "Alert status, firing or resolved", nullable = false)
        @NotBlank(message = "Alert status is required")
        String status,

        // Identifying labels, alertname among them.
        @Schema(description = "Alert labels", nullable = true)
        Map<String, String> labels,

        // Descriptive annotations such as summary, description and runbook.
        @Schema(description = "Alert annotations", nullable = true)
        Map<String, String> annotations,

        // When the alert started firing.
        @Schema(description = "Start time of the alert", nullable = true)
        Instant startsAt,

        // When the alert resolved; 0001-01-01T00:00:00Z while it is firing.
        @Schema(description = "End time of the alert", nullable = true)
        Instant endsAt,

        // Link to the expression that produced the alert.
        @Schema(description = "Generator URL", nullable = true)
        String generatorURL,

        // Stable identity of the alert, stored as the ticket's incident key to find its open ticket.
        @Schema(description = "Alert fingerprint", nullable = false)
        @NotBlank(message = "Alert fingerprint is required")
        @Size(max = AlertmanagerConstant.FINGERPRINT_MAX_LENGTH,
                message = "Alert fingerprint must be at most 64 characters")
        String fingerprint
) {
}
