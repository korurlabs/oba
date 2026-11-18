package com.korur.oba.apps.kaizen.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.enums.HomeLeaderboardPeriod;

/**
 * The home leaderboard: who finished the most work on the reader's boards, weighted by
 * ticket size. The period is the one counted, which is LAST_30_DAYS when SPRINT was
 * asked for but no sprint is ACTIVE on the reader's boards.
 */
@Schema(description = "Home leaderboard")
public record HomeLeaderboardResponse(
        @Schema(description = "The period counted; LAST_30_DAYS when SPRINT was asked for without an active sprint",
                example = "SPRINT")
        HomeLeaderboardPeriod period,

        @Schema(description = "Start of the window, inclusive; null for SPRINT", nullable = true,
                example = "2026-10-05T09:00:00Z")
        Instant from,

        @Schema(description = "End of the window, exclusive; null for SPRINT", nullable = true,
                example = "2026-11-04T09:00:00Z")
        Instant to,

        @Schema(description = "Every credited user, most points first, then most tickets, then last name")
        List<HomeLeaderboardEntryResponse> entries
) {
}
