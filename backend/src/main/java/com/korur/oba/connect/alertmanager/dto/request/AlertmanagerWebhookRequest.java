package com.korur.oba.connect.alertmanager.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.alertmanager.constant.AlertmanagerConstant;

/**
 * Alertmanager webhook notification (payload version 4). Unknown fields are ignored.
 */
@Schema(description = "Alertmanager webhook notification")
public record AlertmanagerWebhookRequest(

        // Payload version, 4 today.
        @Schema(description = "Payload version", nullable = true)
        String version,

        // Key of the alert group.
        @Schema(description = "Group key", nullable = true)
        String groupKey,

        // Number of alerts left out because of max_alerts.
        @Schema(description = "Number of truncated alerts", nullable = true)
        Integer truncatedAlerts,

        // Status of the group, firing or resolved.
        @Schema(description = "Group status", nullable = true)
        String status,

        // Name of the receiver that sent the notification.
        @Schema(description = "Receiver name", nullable = true)
        String receiver,

        // Labels the alerts were grouped by.
        @Schema(description = "Group labels", nullable = true)
        Map<String, String> groupLabels,

        // Labels common to every alert of the group.
        @Schema(description = "Common labels", nullable = true)
        Map<String, String> commonLabels,

        // Annotations common to every alert of the group.
        @Schema(description = "Common annotations", nullable = true)
        Map<String, String> commonAnnotations,

        // Link back to the Alertmanager that sent the notification.
        @Schema(description = "Alertmanager external URL", nullable = true)
        String externalURL,

        // Alerts of the group, bounded so one notification cannot hold the board lock for long.
        @Schema(description = "Alerts", nullable = false)
        @NotNull(message = "Alerts are required")
        @Size(max = AlertmanagerConstant.MAX_ALERTS, message = "At most 50 alerts are allowed")
        List<@Valid @NotNull(message = "Alert must not be null") AlertmanagerAlertRequest> alerts
) {
}
