package com.korur.oba.apps.kaizen.ticket.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Min;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.enums.SubTicketMoveMode;

/**
 * Request payload used to move a ticket between sprint and backlog.
 */
@Schema(description = "Ticket move request")
public record TicketMoveRequest(

        // Target sprint identifier to move the ticket to.
        @Schema(description = "Sprint id", nullable = true)
        UUID sprintId,

        // Target backlog identifier to move the ticket to.
        @Schema(description = "Backlog id", nullable = true)
        UUID backlogId,

        // Board-wide display order that places the ticket between the neighbours the user dropped
        // it on, so the move and its position commit together; null keeps the current order.
        @Schema(description = "Display order to give the ticket on its board, with the sibling shift "
                + "of an order update; omitted keeps the current order", nullable = true, example = "12")
        @Min(value = 0, message = "Order must be at least 0")
        Integer order,

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
        List<UUID> subTicketIds
) {
}
