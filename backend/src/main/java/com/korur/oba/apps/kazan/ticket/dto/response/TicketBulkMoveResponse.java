package com.korur.oba.apps.kazan.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * Result of a bulk move, including the tickets that lost their milestone
 * because they moved to a board unrelated to the milestone's board.
 */
@Schema(description = "Ticket bulk move response")
public record TicketBulkMoveResponse(
        @Schema(description = "Number of requested tickets moved", example = "3")
        int movedCount,

        @Schema(description = "Keys (after the move) of tickets whose milestone was cleared", example = "[\"KAGI-42\"]")
        List<String> milestoneClearedTicketKeys
) {
}
