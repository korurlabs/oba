package com.korur.oba.apps.kaizen.backlog.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

import com.korur.oba.apps.kaizen.ticket.dto.response.TicketListResponse;

/**
 * Response payload for a detailed backlog view.
 */
@Schema(description = "Backlog detail response")
public record BacklogDetailResponse(

        // Unique backlog identifier.
        @Schema(description = "Backlog id", nullable = false)
        UUID id,

        // Board identifier this backlog belongs to.
        @Schema(description = "Board id", nullable = false)
        UUID boardId,

        // List of tickets in the backlog.
        @Schema(description = "Tickets", nullable = true)
        List<TicketListResponse> tickets
) {
}
