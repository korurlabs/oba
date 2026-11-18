package com.korur.oba.apps.kaizen.sprint.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.enums.TicketRelocationAction;

/**
 * Request payload for sprint deletion with configurable handling for tickets.
 */
@Schema(description = "Sprint delete request")
public record SprintDeleteRequest(

        // Action to perform on sprint tickets when deleting the sprint.
        @Schema(description = "Action for sprint tickets: MOVE_TO_BACKLOG, MOVE_TO_SPRINT, DELETE", nullable = true)
        TicketRelocationAction ticketAction,

        // Target sprint when ticketAction is MOVE_TO_SPRINT.
        @Schema(description = "Target sprint when ticketAction=MOVE_TO_SPRINT", nullable = true)
        UUID targetSprintId
) {
}
