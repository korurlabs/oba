package com.korur.oba.apps.kaizen.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Response payload describing a ticket reviewer.
 */
@Schema(description = "Ticket reviewer response")
public record TicketReviewerResponse(

        // Unique reviewer record identifier.
        @Schema(description = "Reviewer id", nullable = false)
        UUID id,

        // User identifier for the reviewer.
        @Schema(description = "User id", nullable = false)
        UUID userId,

        // Reviewer email address.
        @Schema(description = "User email", nullable = false)
        String email,

        // Reviewer first name.
        @Schema(description = "User first name", nullable = false)
        String firstName,

        // Reviewer last name.
        @Schema(description = "User last name", nullable = false)
        String lastName
) {
}
