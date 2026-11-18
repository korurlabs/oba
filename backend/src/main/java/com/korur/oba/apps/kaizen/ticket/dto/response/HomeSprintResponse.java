package com.korur.oba.apps.kaizen.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.dto.response.BoardListResponse;

/**
 * The ACTIVE sprint of the home page's focus board, with its burndown. The focus board
 * and sprint fields are null when none of the reader's boards runs an ACTIVE sprint.
 * The tickets counted are the sprint's list roots on the reader's boards, so a sprint
 * shared with other boards never reveals their work.
 */
@Schema(description = "Home sprint strip and burndown")
public record HomeSprintResponse(
        @Schema(description = "Focus board id; null without an active sprint", nullable = true)
        UUID boardId,

        @Schema(description = "Focus board name; null without an active sprint", nullable = true)
        String boardName,

        @Schema(description = "Focus board slug; null without an active sprint", nullable = true, example = "korur")
        String boardSlug,

        @Schema(description = "Active sprint id; null without an active sprint", nullable = true)
        UUID sprintId,

        @Schema(description = "Active sprint name; null without an active sprint", nullable = true)
        String sprintName,

        @Schema(description = "Sprint start; null when not set", nullable = true, example = "2026-10-26T06:00:00Z")
        Instant startDate,

        @Schema(description = "Sprint end; null when not set", nullable = true, example = "2026-11-08T20:59:59Z")
        Instant endDate,

        @Schema(description = "Sprint tickets on the reader's boards, sub-tickets under their parent left out",
                example = "24")
        int totalTickets,

        @Schema(description = "Those tickets that are not DONE", example = "12")
        int openTickets,

        @Schema(description = "One point per day from start to end; empty without both dates")
        List<HomeBurndownPointResponse> burndown,

        @Schema(description = "The reader's boards with an active sprint, by name, for the focus select")
        List<BoardListResponse> boards
) {
}
