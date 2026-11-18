package com.korur.oba.apps.kazan.milestone.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.LocalDate;
import java.util.List;

// 4. Internal

/**
 * Roadmap of a board: the board's lane first, then one lane per direct child board.
 */
@Schema(description = "Roadmap response")
public record RoadmapResponse(

        @Schema(description = "Window start as requested (YYYY-MM-DD)", nullable = true, example = "2026-10-01")
        LocalDate from,

        @Schema(description = "Window end as requested (YYYY-MM-DD)", nullable = true, example = "2026-12-31")
        LocalDate to,

        @Schema(description = "Lanes: the board first, then its child boards by name")
        List<RoadmapBoardResponse> boards
) {
}
