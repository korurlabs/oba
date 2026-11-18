package com.korur.oba.apps.kaizen.ticket.dto.response;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;

/**
 * Embedded parent of a listed sub-ticket, so a list can show a sub-ticket that is
 * planned apart from its parent under the parent's title and progress.
 */
@Schema(description = "Ticket parent")
public record TicketParentResponse(
        @Schema(description = "Parent ticket id", nullable = false)
        UUID id,

        @Schema(description = "Parent ticket key", nullable = false, example = "KAIZ-41")
        String key,

        @Schema(description = "Parent ticket title", nullable = false)
        String title,

        @Schema(description = "Parent ticket status", nullable = false, example = "IN_PROGRESS")
        TicketStatus status,

        @Schema(description = "Parent ticket type", nullable = false, example = "FEATURE")
        TicketType type,

        // Every direct sub-ticket of the parent, wherever it is placed.
        @Schema(description = "Status and placement of every direct sub-ticket of the parent", nullable = false)
        List<TicketSubTicketPlacementResponse> subTickets
) {
}
