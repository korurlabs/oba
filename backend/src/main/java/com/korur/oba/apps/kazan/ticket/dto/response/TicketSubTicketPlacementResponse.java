package com.korur.oba.apps.kazan.ticket.dto.response;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;

/**
 * Status and placement of one sub-ticket of a parent, from which a client shows
 * the parent's progress across sprints and the backlog.
 */
@Schema(description = "Sub-ticket placement")
public record TicketSubTicketPlacementResponse(
        @Schema(description = "Sub-ticket id", nullable = false)
        UUID id,

        @Schema(description = "Sub-ticket status", nullable = false, example = "IN_PROGRESS")
        TicketStatus status,

        // The sub-ticket's own sprint; null when it is in the backlog.
        @Schema(description = "Sprint the sub-ticket is planned in; null in the backlog", nullable = true)
        TicketSprintResponse sprint
) {
}
