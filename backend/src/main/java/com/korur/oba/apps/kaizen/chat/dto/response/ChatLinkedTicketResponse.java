package com.korur.oba.apps.kaizen.chat.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * A ticket key typed as plain text that the reader can open. Keys the reader
 * cannot open are left out, so a message reveals nothing about tickets on
 * boards the reader does not belong to.
 */
@Schema(description = "Plain ticket key in a chat message that the reader can open")
public record ChatLinkedTicketResponse(

        @Schema(description = "Ticket key as typed", example = "ACME-218")
        String ticketKey,

        @Schema(description = "Ticket id", example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID ticketId,

        @Schema(description = "Board of the ticket", example = "9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a")
        UUID boardId
) {
}
