package com.korur.oba.apps.kazan.pullrequest.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Response payload for one related ticket of a pull request share. Every
 * reader gets every ticket as stored when it was added; canOpen tells this
 * reader whether the ticket can be opened.
 */
@Schema(description = "Pull request share related ticket response")
public record PullRequestShareTicketResponse(

        @Schema(description = "Ticket id", example = "3f6c1d2e-8b7a-4c1e-9f0a-2b3c4d5e6f70")
        UUID ticketId,

        @Schema(description = "Id of the board the ticket was on when it was added",
                example = "9a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9")
        UUID boardId,

        @Schema(description = "Board name when the ticket was added", example = "Fabrikam Board")
        String boardName,

        @Schema(description = "Ticket key when the ticket was added", example = "ACME-241")
        String ticketKey,

        @Schema(description = "Ticket title when the ticket was added", example = "Sidebar refactor")
        String ticketTitle,

        @Schema(description = "True when the ticket exists and the reader is a member of its board", example = "true")
        boolean canOpen
) {
}
