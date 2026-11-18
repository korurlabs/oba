package com.korur.oba.apps.kaizen.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.math.BigDecimal;
import java.time.LocalDate;

// 4. Internal

/**
 * One day of the home sprint burndown, on the Istanbul calendar of the daily updates.
 */
@Schema(description = "Home sprint burndown point")
public record HomeBurndownPointResponse(
        @Schema(description = "The day, in Europe/Istanbul", example = "2026-11-04")
        LocalDate date,

        @Schema(description = "Tickets still open at the end of the day; null for a day after today",
                nullable = true, example = "12")
        Integer remaining,

        @Schema(description = "Ideal remaining tickets, falling evenly from the total on the first day to 0 "
                + "on the last, one decimal", example = "10.5")
        BigDecimal ideal
) {
}
