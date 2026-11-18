package com.korur.oba.apps.kazan.ticket.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.ticket.enums.SubTicketMoveMode;
import com.korur.oba.apps.kazan.ticket.enums.TicketRelocationAction;

/**
 * Request payload for bulk-moving tickets to a different sprint or backlog.
 */
@Schema(description = "Ticket bulk move request")
public record TicketBulkMoveRequest(

        // IDs of the tickets to move.
        @NotNull
        @Size(min = 1)
        @Schema(description = "IDs of the tickets to move", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UUID> ticketIds,

        // Relocation action (DELETE is not allowed for bulk move).
        @NotNull
        @Schema(description = "Action: MOVE_TO_BACKLOG or MOVE_TO_SPRINT (DELETE not allowed)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        TicketRelocationAction action,

        // Target sprint when action is MOVE_TO_SPRINT.
        @Schema(description = "Target sprint when action=MOVE_TO_SPRINT", nullable = true)
        UUID targetSprintId,

        // Target board for cross-board relocation (null = same board).
        @Schema(description = "Target board for cross-board relocation (null = same board)", nullable = true)
        UUID targetBoardId,

        // Which sub-tickets of the moved tickets come along; null means UNFINISHED. A move to
        // another board ignores it and carries every sub-ticket.
        @Schema(description = "Which sub-tickets come along: NONE, UNFINISHED (not DONE and placed where "
                + "the moved ticket was) or SELECTED (those in subTicketIds); omitted means UNFINISHED. "
                + "Ignored on a move to another board, which carries every sub-ticket",
                nullable = true, example = "UNFINISHED")
        SubTicketMoveMode subTicketMode,

        // Descendants to move along with subTicketMode SELECTED, wherever they are placed.
        @Schema(description = "Sub-tickets, at any depth, of the moved tickets to move along; "
                + "only with subTicketMode SELECTED", nullable = true)
        List<UUID> subTicketIds,

        // On a move to another board, true leaves the direct sub-tickets on their board as
        // top-level tickets instead of taking them along; null or false takes the whole subtree.
        @Schema(description = "Only on a move to another board: true leaves the direct sub-tickets of "
                + "the moved tickets on their board as top-level tickets, each with its own sub-tickets "
                + "and placement; omitted or false takes the whole subtree along. Ignored when no ticket "
                + "changes board", nullable = true, example = "false")
        Boolean detachSubTickets
) {
}
