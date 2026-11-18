package com.korur.oba.connect.alertmanager.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

/**
 * Outcome of one Alertmanager notification, counted per alert.
 */
@Schema(description = "Alertmanager webhook response")
public record AlertmanagerWebhookResponse(

        // Alerts that became a new incident ticket.
        @Schema(description = "Tickets created", nullable = false)
        int created,

        // Alerts that changed nothing.
        @Schema(description = "Alerts ignored", nullable = false)
        int ignored
) {
}
