package com.korur.oba.connect.azure.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.enums.AzureConnectionStatus;

/**
 * Response payload for one Azure connection of an organization, with the subscriptions of its last
 * successful check and the values its federated credential must name. It never holds a credential.
 */
@Schema(description = "Azure connection response")
public record AzureConnectionResponse(
        @Schema(description = "Connection id", example = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b")
        UUID id,

        @Schema(description = "Connection name", example = "Production")
        String name,

        @Schema(description = "Directory (tenant) id", example = "3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID tenantId,

        @Schema(description = "Client id of the identity; null until set", nullable = true,
                example = "9a8b7c6d-5e4f-4a3b-2c1d-0e9f8a7b6c5d")
        UUID clientId,

        @Schema(description = "PENDING until a check succeeds, CONNECTED after one, FAILING when a later check "
                + "failed", example = "CONNECTED")
        AzureConnectionStatus status,

        @Schema(description = "Last successful check; null before the first one and after an identity change",
                nullable = true, example = "2026-11-28T04:30:00Z")
        Instant lastVerifiedAt,

        @Schema(description = "Error code of the latest failed check, with Entra's AADSTS code after a colon for "
                + "AZR_UPS_009; null after a success", nullable = true, example = "AZR_UPS_009:AADSTS700213")
        String lastError,

        @Schema(description = "Creation timestamp", example = "2026-11-28T09:30:00Z")
        Instant createdAt,

        @Schema(description = "Subscriptions the identity could read at the last successful check")
        List<AzureConnectionSubscriptionResponse> subscriptions,

        @Schema(description = "Values of the federated credential to create in Azure")
        AzureConnectionSetupResponse setup
) {
}
