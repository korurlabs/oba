package com.korur.oba.apps.kaya.account.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.account.dto.response.embedded.AccountUserEmbeddedResponse;
import com.korur.oba.apps.kaya.account.enums.AccountProvider;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineUsageResponse;

@Schema(description = "An account of the organization in full, with its users, machines and current usage")
public record AccountDetailResponse(
        @Schema(description = "Account id", example = "7e1d2c3b-4a5f-4e6d-9c8b-1a2b3c4d5e6f")
        UUID id,

        @Schema(description = "AI provider of the account", example = "CLAUDE")
        AccountProvider provider,

        @Schema(description = "Email of the account", example = "ada@example.com")
        String accountEmail,

        @Schema(description = "UUID of the provider organization; null for a personal account", nullable = true,
                example = "0b1c2d3e-4f5a-4b6c-8d7e-9f0a1b2c3d4e")
        String accountOrganizationUuid,

        @Schema(description = "Name of the provider organization; null for a personal account", nullable = true,
                example = "Korur")
        String accountOrganizationName,

        @Schema(description = "Billing type as reported by the recorder", nullable = true, example = "stripe_subscription")
        String billingType,

        @Schema(description = "Organization type as reported by the recorder", nullable = true, example = "claude_team")
        String organizationType,

        @Schema(description = "Seat tier as reported by the recorder", nullable = true, example = "premium")
        String seatTier,

        @Schema(description = "When any user last used the account")
        Instant lastSeenAt,

        @Schema(description = "The users linked to the account, by display name")
        List<AccountUserEmbeddedResponse> users,

        @Schema(description = "Machines whose latest account is this one, RETIRED ones included", example = "3")
        long machines,

        @Schema(description = "ACTIVE machines whose latest account is this one", example = "2")
        long activeMachines,

        @Schema(description = "Usage limits from the account's newest snapshot; KNOWN or NO_SNAPSHOT, sharedWith empty")
        MachineUsageResponse usage,

        @Schema(description = "When the account was first recorded")
        Instant createdAt,

        @Schema(description = "When the account was last changed")
        Instant updatedAt
) {
}
