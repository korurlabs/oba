package com.korur.oba.apps.kazan.sprint.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

/**
 * A board whose ACTIVE sprint a start with takeover would close, with what happens to its
 * unfinished tickets.
 */
@Schema(description = "Board linked to an ACTIVE sprint that a start with takeover would close")
public record SprintStartConflictResponse(

        // Board that holds the blocking sprint.
        @Schema(description = "Board id", nullable = false)
        UUID boardId,

        // Board name shown in the list.
        @Schema(description = "Board name", nullable = false, example = "Fabrikam")
        String boardName,

        // Board ticket prefix shown next to the name.
        @Schema(description = "Board prefix", nullable = false, example = "ACME")
        String boardPrefix,

        // The ACTIVE sprint that would be closed.
        @Schema(description = "Id of the blocking sprint", nullable = false)
        UUID sprintId,

        // Name of the sprint that would be closed.
        @Schema(description = "Name of the blocking sprint", nullable = false, example = "ACME 12")
        String sprintName,

        // Tickets of the blocking sprint on this board that are not DONE, the ones that move.
        @Schema(description = "Number of tickets of the blocking sprint on this board that are not DONE",
                nullable = false, example = "7")
        long unfinishedTicketCount,

        // A board not linked to the starting sprint gets its unfinished tickets in its backlog.
        @Schema(description = "Whether the unfinished tickets go to the board's backlog, because the board "
                + "is not linked to the sprint being started", nullable = false)
        boolean movesToBacklog,

        // True when the caller is ADMIN or OWNER of this board, which the takeover needs.
        @Schema(description = "Whether the caller is ADMIN or OWNER of the board", nullable = false)
        boolean canManage,

        // Set when closing the sprint leaves its repeating series nothing to run, which switches it off.
        @Schema(description = "Name of the repeating series that stops when the blocking sprint is closed, "
                + "because the series has no other running or planned sprint; null when nothing stops",
                nullable = true, example = "Sprint")
        String endsSeriesName
) {
}
