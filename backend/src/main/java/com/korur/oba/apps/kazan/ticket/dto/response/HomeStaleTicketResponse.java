package com.korur.oba.apps.kazan.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.ticket.enums.MissionRank;
import com.korur.oba.apps.kazan.ticket.enums.TicketPriority;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;

/**
 * A backlog ticket nobody edited or commented on for the stale window, shown on the
 * home page so it can be reviewed and deleted.
 */
@Schema(description = "Stale backlog ticket on the home page")
public record HomeStaleTicketResponse(
        @Schema(description = "Ticket id", example = "5f0c1c2e-8a4b-4f7e-9d43-1b2a3c4d5e6f")
        UUID id,

        @Schema(description = "Ticket key", example = "KAI-142")
        String key,

        @Schema(description = "Ticket title", example = "Export the sprint report as PDF")
        String title,

        @Schema(description = "Ticket status, never DONE", example = "TODO")
        TicketStatus status,

        @Schema(description = "Ticket priority", example = "MEDIUM")
        TicketPriority priority,

        @Schema(description = "Ticket size", example = "M")
        MissionRank missionRank,

        @Schema(description = "Id of the ticket's board", example = "0b6f7f3a-2d1e-4c5b-8a9d-7e6f5a4b3c2d")
        UUID boardId,

        @Schema(description = "Name of the ticket's board", example = "Kazan")
        String boardName,

        @Schema(description = "URL slug of the ticket's board", example = "kazan")
        String boardSlug,

        @Schema(description = "The later of the last edit and the newest comment", example = "2026-09-21T08:15:00Z")
        Instant lastActivityAt,

        @Schema(description = "Whether the reader may delete the ticket: MEMBER, ADMIN or OWNER of its board",
                example = "true")
        boolean canDelete
) {
}
