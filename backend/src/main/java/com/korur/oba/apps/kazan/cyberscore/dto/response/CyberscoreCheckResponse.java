package com.korur.oba.apps.kazan.cyberscore.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckCategory;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckOutcome;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckStatus;

/**
 * Response payload for one check of an organization.
 */
@Schema(description = "Check response")
public record CyberscoreCheckResponse(

        @Schema(description = "Check id", example = "6f7a8b9c-0d1e-4f2a-8b3c-4d5e6f7a8b9c")
        UUID id,

        @Schema(description = "Check name", example = "Multi-factor sign-in for every account")
        String name,

        @Schema(description = "What the check covers", nullable = true,
                example = "Every user and admin account signs in with a second factor.")
        String description,

        @Schema(description = "Expected state of a passing check",
                example = "No account can sign in with a password alone.")
        String expected,

        @Schema(description = "Weight in the score, 1 to 100", example = "10")
        int weight,

        @Schema(description = "Position within the category, 0 to 9999", example = "1")
        int sortOrder,

        // Null for an uncategorized check.
        @Schema(description = "Category; null for an uncategorized check", nullable = true, example = "IDENTITY")
        CyberscoreCheckCategory category,

        @Schema(description = "Current outcome", example = "PASS")
        CyberscoreCheckOutcome outcome,

        @Schema(description = "Workflow status", example = "DONE")
        CyberscoreCheckStatus status,

        @Schema(description = "Last update timestamp", example = "2026-11-25T09:30:00Z")
        Instant updatedAt
) {
}
