package com.korur.oba.apps.kaizen.ticket.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Min;

// 3. Java SDK

// 4. Internal

/**
 * Request payload used to update the display order of a ticket.
 */
@Schema(description = "Ticket order update request")
public record TicketOrderUpdateRequest(

        // New display order position for the ticket.
        @Schema(description = "Display order", nullable = false)
        @Min(value = 0, message = "Order must be at least 0")
        int order
) {
}
