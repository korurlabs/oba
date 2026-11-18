package com.korur.oba.apps.kaizen.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.math.BigDecimal;
import java.util.UUID;

// 4. Internal

/**
 * One person on the home leaderboard with the size points and the number of finished
 * tickets credited to them.
 */
@Schema(description = "Home leaderboard entry")
public record HomeLeaderboardEntryResponse(
        @Schema(description = "User id", example = "9a8b7c6d-5e4f-4a3b-2c1d-0e9f8a7b6c5d")
        UUID userId,

        @Schema(description = "First name", nullable = true, example = "Ayşe")
        String firstName,

        @Schema(description = "Last name", nullable = true, example = "Yılmaz")
        String lastName,

        @Schema(description = "Email address", example = "ayse.yilmaz@example.com")
        String email,

        @Schema(description = "Size points, split evenly between a ticket's assignees, one decimal", example = "12.5")
        BigDecimal points,

        @Schema(description = "Finished tickets the user is assigned to", example = "6")
        int tickets
) {
}
