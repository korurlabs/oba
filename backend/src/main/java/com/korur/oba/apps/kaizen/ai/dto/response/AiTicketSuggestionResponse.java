package com.korur.oba.apps.kaizen.ai.dto.response;

import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload for a single AI-generated ticket suggestion.
 */
@Schema(description = "AI ticket suggestion")
public record AiTicketSuggestionResponse(

        // Suggested ticket title.
        @Schema(description = "Ticket title", nullable = false)
        String title,

        // Suggested ticket description.
        @Schema(description = "Ticket description", nullable = false)
        String description,

        // Suggested ticket type.
        @Schema(description = "Ticket type", nullable = false)
        String type,

        // Suggested ticket priority.
        @Schema(description = "Ticket priority", nullable = false)
        String priority
) {
}
