package com.korur.oba.apps.kazan.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Response payload describing the user who created a ticket. A ticket created
 * by a system actor has no user creator; see TicketCreatedBySystemActorResponse.
 */
@Schema(description = "Ticket created by response")
public record TicketCreatedByResponse(

        // User identifier for the creator.
        @Schema(description = "User id", nullable = false)
        UUID userId,

        // Creator email address.
        @Schema(description = "User email", nullable = false)
        String email,

        // Creator first name.
        @Schema(description = "User first name", nullable = false)
        String firstName,

        // Creator last name.
        @Schema(description = "User last name", nullable = false)
        String lastName
) {
}
