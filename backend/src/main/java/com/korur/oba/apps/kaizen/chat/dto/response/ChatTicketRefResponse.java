package com.korur.oba.apps.kaizen.chat.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * A ticket referenced with a ticket token. Key and title are the snapshot of
 * send time and shown to every reader; canOpen tells the reader whether the
 * chip links to the ticket.
 */
@Schema(description = "Ticket reference of a chat message")
public record ChatTicketRefResponse(

        @Schema(description = "Ticket id; null when the ticket row was removed", nullable = true,
                example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID ticketId,

        @Schema(description = "Board of the ticket at send time", example = "9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a")
        UUID boardId,

        @Schema(description = "Ticket key at send time", example = "ACME-218")
        String ticketKey,

        @Schema(description = "Ticket title at send time", example = "Mobile Menu Figma Design")
        String ticketTitle,

        @Schema(description = "True when the reader is a member of the ticket's board and the ticket is live",
                example = "true")
        boolean canOpen
) {
}
