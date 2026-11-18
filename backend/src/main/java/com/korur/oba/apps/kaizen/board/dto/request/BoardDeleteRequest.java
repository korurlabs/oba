package com.korur.oba.apps.kaizen.board.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.enums.BoardChildBoardAction;
import com.korur.oba.apps.kaizen.ticket.enums.TicketRelocationAction;

/**
 * Request payload for board deletion with configurable handling for tickets and child boards.
 */
@Schema(description = "Board delete request")
public record BoardDeleteRequest(

        // Action to perform on tickets when deleting the board.
        @Schema(description = "Action for tickets: MOVE_TO_BACKLOG, MOVE_TO_SPRINT, or DELETE", nullable = true)
        TicketRelocationAction ticketAction,

        // Destination board when ticketAction is MOVE_TO_BACKLOG or MOVE_TO_SPRINT.
        @Schema(description = "Destination board when ticketAction=MOVE_TO_BACKLOG or MOVE_TO_SPRINT", nullable = true)
        UUID targetBoardId,

        // Destination sprint when ticketAction is MOVE_TO_SPRINT.
        @Schema(description = "Destination sprint when ticketAction=MOVE_TO_SPRINT", nullable = true)
        UUID targetSprintId,

        // Action to perform on child boards when deleting the board.
        @Schema(description = "Action for child boards: PROMOTE, MOVE, or DELETE", nullable = true)
        BoardChildBoardAction childBoardAction,

        // New parent board when childBoardAction is MOVE.
        @Schema(description = "New parent board when childBoardAction=MOVE", nullable = true)
        UUID newParentBoardId
) {
}
