package com.korur.oba.apps.kazan.alertroute.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * Response payload for an alert route.
 */
@Schema(description = "Alert route response")
public record AlertRouteResponse(

        // Unique alert route identifier.
        @Schema(description = "Alert route id", example = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b")
        UUID id,

        // Value of the alert's system label.
        @Schema(description = "System that sends the alerts", example = "fabrikam")
        String systemKey,

        // Board that receives the system's incidents.
        @Schema(description = "Board id", example = "4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a")
        UUID boardId,

        // Board name shown in UI.
        @Schema(description = "Board name", example = "Fabrikam")
        String boardName,

        // Board prefix of the ticket keys.
        @Schema(description = "Board prefix", example = "ACME")
        String boardPrefix,

        // Board colour shown as a dot.
        @Schema(description = "Board colour", nullable = true, example = "#4AA3FF")
        String boardColor,

        // Whether this route also receives the alerts of unmapped systems.
        @Schema(description = "Default board for unmapped systems", example = "false")
        boolean isDefault,

        // Last change of the route.
        @Schema(description = "Last update timestamp", example = "2026-10-24T09:30:00Z")
        Instant updatedAt
) {
}
