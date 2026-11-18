package com.korur.oba.apps.kaizen.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * Response payload describing a comment on a ticket.
 */
@Schema(description = "Ticket comment response")
public record TicketCommentResponse(

        // Unique comment identifier.
        @Schema(description = "Comment id", nullable = false)
        UUID id,

        // Comment creation timestamp.
        @Schema(description = "Created at", nullable = false)
        Instant createdAt,

        // Comment update timestamp.
        @Schema(description = "Updated at", nullable = false)
        Instant updatedAt,

        // Comment content text.
        @Schema(description = "Comment content", nullable = false)
        String content,

        // Author user identifier.
        @Schema(description = "Author user id", nullable = false)
        UUID authorId,

        // Author email address.
        @Schema(description = "Author email", nullable = false)
        String authorEmail,

        // Author first name.
        @Schema(description = "Author first name", nullable = false)
        String authorFirstName,

        // Author last name.
        @Schema(description = "Author last name", nullable = false)
        String authorLastName
) {
}
