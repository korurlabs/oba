package com.korur.oba.apps.kaya.machine.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.account.enums.AccountProvider;

@Schema(description = "The account last seen on a machine and its subscription")
public record MachineAccountEmbeddedResponse(
        @Schema(description = "Account id", example = "7e1d2c3b-4a5f-4e6d-9c8b-1a2b3c4d5e6f")
        UUID id,

        @Schema(description = "AI provider of the account", example = "CLAUDE")
        AccountProvider provider,

        @Schema(description = "Email of the account", example = "ada@example.com")
        String accountEmail,

        @Schema(description = "UUID of the provider organization; null for a personal account", nullable = true)
        String accountOrganizationUuid,

        @Schema(description = "Name of the provider organization", nullable = true, example = "Korur")
        String accountOrganizationName,

        @Schema(description = "Billing type as reported by the recorder", nullable = true, example = "stripe_subscription")
        String billingType,

        @Schema(description = "Organization type as reported by the recorder", nullable = true, example = "claude_team")
        String organizationType,

        @Schema(description = "Seat tier as reported by the recorder", nullable = true, example = "premium")
        String seatTier
) {
}
