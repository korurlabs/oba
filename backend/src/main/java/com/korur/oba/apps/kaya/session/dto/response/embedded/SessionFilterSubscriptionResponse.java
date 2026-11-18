package com.korur.oba.apps.kaya.session.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

@Schema(description = "A Claude subscription (Claude organization) sessions ran on, as a wall filter option")
public record SessionFilterSubscriptionResponse(
        @Schema(description = "Claude organization UUID; the value of the accountOrganizationUuid filter")
        String uuid,

        @Schema(description = "Claude organization name", nullable = true, example = "Korur")
        String name,

        @Schema(description = "Billing type of the newest session on it", nullable = true, example = "stripe_subscription")
        String billingType,

        @Schema(description = "Seat tier of the newest session on it", nullable = true, example = "team_premium")
        String seatTier
) {
}
