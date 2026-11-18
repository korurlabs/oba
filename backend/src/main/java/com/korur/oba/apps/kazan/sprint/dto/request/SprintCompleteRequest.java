package com.korur.oba.apps.kazan.sprint.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.ticket.enums.TicketRelocationAction;

/**
 * Request payload for completing a sprint with configurable handling for incomplete tickets.
 */
@Schema(description = "Sprint complete request")
public record SprintCompleteRequest(

        // Action to perform on incomplete tickets when completing the sprint.
        @NotNull
        @Schema(description = "Action for incomplete tickets: MOVE_TO_BACKLOG, MOVE_TO_SPRINT, or DELETE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        TicketRelocationAction ticketAction,

        // Target sprint when ticketAction is MOVE_TO_SPRINT.
        @Schema(description = "Target sprint when ticketAction=MOVE_TO_SPRINT", nullable = true)
        UUID targetSprintId,

        // Target board for cross-board relocation (null = same board).
        @Schema(description = "Target board for cross-board relocation (null = same board)", nullable = true)
        UUID targetBoardId,

        // Moves unfinished tickets into the next round of the sprint's repeating series (null = false).
        @Schema(description = "Move unfinished tickets to the next sprint of the sprint's repeating series; "
                + "requires ticketAction=MOVE_TO_SPRINT and no target sprint or board", nullable = true)
        Boolean moveToNextSprint
) {
}
