package com.korur.oba.apps.kaya.ingest.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

/**
 * The Claude account fields the recorder copies from ~/.claude.json. Every field may be missing
 * or empty when the file is partial; an account without an email address is not learned. Values
 * longer than their columns are cut, because the recorder copies them verbatim.
 */
@Schema(description = "The Claude account Claude Code is logged in with")
public record IngestAccountRequest(
        @Schema(description = "Account email", nullable = true, example = "ada@example.com")
        String emailAddress,

        @Schema(description = "Claude organization name", nullable = true, example = "Korur")
        String organizationName,

        @Schema(description = "Claude organization UUID; null for a personal account", nullable = true)
        String organizationUuid,

        @Schema(description = "Billing type", nullable = true, example = "stripe_subscription")
        String billingType,

        @Schema(description = "Organization type", nullable = true, example = "claude_team")
        String organizationType,

        @Schema(description = "Seat tier", nullable = true, example = "premium")
        String seatTier
) {
}
