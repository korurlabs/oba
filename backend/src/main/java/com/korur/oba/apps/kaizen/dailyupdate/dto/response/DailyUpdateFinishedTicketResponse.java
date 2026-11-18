package com.korur.oba.apps.kaizen.dailyupdate.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.enums.MissionRank;
import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;

/**
 * Response payload for one ticket the author of a daily update finished in a
 * running sprint. Read live for the reader, so it lists only tickets on boards
 * the reader is a member of, and every listed ticket can be opened.
 */
@Schema(description = "Ticket the author finished in an active sprint")
public record DailyUpdateFinishedTicketResponse(

        @Schema(description = "Ticket id", example = "3f6c1d2e-8b7a-4c1e-9f0a-2b3c4d5e6f70")
        UUID ticketId,

        @Schema(description = "Id of the board the ticket is on", example = "9a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9")
        UUID boardId,

        @Schema(description = "Name of the board the ticket is on", example = "Fabrikam Board")
        String boardName,

        @Schema(description = "Ticket key", example = "ACME-214")
        String ticketKey,

        @Schema(description = "Ticket title", example = "Checkout Page Figma Design")
        String ticketTitle,

        @Schema(description = "Mission Rank of the ticket", example = "M")
        MissionRank missionRank,

        @Schema(description = "Priority of the ticket", example = "HIGH")
        TicketPriority priority
) {
}
