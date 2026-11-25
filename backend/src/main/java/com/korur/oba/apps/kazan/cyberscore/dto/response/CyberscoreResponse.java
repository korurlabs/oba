package com.korur.oba.apps.kazan.cyberscore.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;

/**
 * Response payload for the current cyberscore of an organization.
 */
@Schema(description = "Current cyberscore of the organization")
public record CyberscoreResponse(

        // Weighted score over the scorable checks; 0 when none is scorable.
        @Schema(description = "Score percentage, 0 to 100", example = "72")
        int scorePercentage,

        // Risk band of the percentage; UNKNOWN when no check is scorable.
        @Schema(description = "Risk band of the score", example = "LOW")
        CyberscoreLevel scoreLevel,

        // Percentage per category name; a category without scorable checks has no key.
        @Schema(description = "Percentage per check category", example = "{\"DEVICES\": 80, \"GDPR_AVG\": 50}")
        Map<String, Integer> breakdownByCategory,

        // Number of checks whose outcome counts toward the score.
        @Schema(description = "Number of scorable checks", example = "12")
        int scorableCheckCount,

        // Last recalculation; null before the first one.
        @Schema(description = "Last recalculation timestamp", nullable = true, example = "2026-11-25T09:30:00Z")
        Instant lastCalculatedAt
) {
}
