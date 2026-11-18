package com.korur.oba.apps.kaizen.ticket.dto.response;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

/**
 * Embedded backlog info for the all-tickets listing.
 */
@Schema(description = "Ticket backlog")
public record TicketBacklogResponse(
        @Schema(description = "Backlog id", nullable = false)
        UUID id
) {
}
