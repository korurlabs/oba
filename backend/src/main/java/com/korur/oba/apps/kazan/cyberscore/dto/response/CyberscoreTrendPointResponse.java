package com.korur.oba.apps.kazan.cyberscore.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.LocalDate;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;

/**
 * Response payload for one day of the cyberscore trend.
 */
@Schema(description = "Cyberscore of one business day")
public record CyberscoreTrendPointResponse(

        // Business day the score held at its end.
        @Schema(description = "Business day", example = "2026-11-25")
        LocalDate date,

        @Schema(description = "Score percentage, 0 to 100", example = "72")
        int scorePercentage,

        @Schema(description = "Risk band of the score", example = "LOW")
        CyberscoreLevel scoreLevel
) {
}
