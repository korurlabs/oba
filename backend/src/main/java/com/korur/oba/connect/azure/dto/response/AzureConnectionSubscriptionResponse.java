package com.korur.oba.connect.azure.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * A subscription the identity of a connection could read at the last successful check.
 */
@Schema(description = "Azure subscription readable through a connection")
public record AzureConnectionSubscriptionResponse(
        @Schema(description = "Azure subscription id", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID subscriptionId,

        @Schema(description = "Name Azure reports for the subscription", example = "Production")
        String displayName,

        @Schema(description = "State Azure reports, such as Enabled, Warned, PastDue or Disabled", example = "Enabled")
        String state
) {
}
