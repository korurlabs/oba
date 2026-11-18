package com.korur.oba.apps.kaizen.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Response payload describing a ticket assignee.
 */
@Schema(description = "Ticket assignee response")
public record TicketAssigneeResponse(

        // Unique assignee record identifier.
        @Schema(description = "Assignee id", nullable = false)
        UUID id,

        // User identifier for the assignee.
        @Schema(description = "User id", nullable = false)
        UUID userId,

        // Assignee email address.
        @Schema(description = "User email", nullable = false)
        String email,

        // Assignee first name.
        @Schema(description = "User first name", nullable = false)
        String firstName,

        // Assignee last name.
        @Schema(description = "User last name", nullable = false)
        String lastName
) {
}
