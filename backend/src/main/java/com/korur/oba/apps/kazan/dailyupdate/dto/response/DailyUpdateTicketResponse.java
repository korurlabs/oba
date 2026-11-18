package com.korur.oba.apps.kazan.dailyupdate.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.ticket.enums.MissionRank;
import com.korur.oba.apps.kazan.ticket.enums.TicketPriority;

/**
 * Response payload for one ticket line of a daily update. Every reader gets
 * every line; canOpen tells this reader whether the ticket can be opened, and
 * missionRank and priority are the ticket's rank and priority now, read only
 * when the reader can open it.
 */
@Schema(description = "Daily update ticket line response")
public record DailyUpdateTicketResponse(

        @Schema(description = "Ticket id; null once the ticket was removed", nullable = true,
                example = "3f6c1d2e-8b7a-4c1e-9f0a-2b3c4d5e6f70")
        UUID ticketId,

        @Schema(description = "Id of the board the ticket was on when the line was added",
                example = "9a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9")
        UUID boardId,

        @Schema(description = "Board name when the line was added", example = "Fabrikam Board")
        String boardName,

        @Schema(description = "Ticket key when the line was added", example = "ACME-218")
        String ticketKey,

        @Schema(description = "Ticket title when the line was added", example = "Mobile Menu Figma Design")
        String ticketTitle,

        @Schema(description = "Progress the author reported, 0 to 100", example = "30")
        int percentage,

        @Schema(description = "Note under the ticket line", nullable = true, example = "Waiting for the final icons")
        String note,

        @Schema(description = "True when the line wrote its percentage to the ticket; in a preview, when it would",
                example = "true")
        boolean progressSynced,

        @Schema(description = "True when the ticket exists and the reader is a member of its board", example = "true")
        boolean canOpen,

        @Schema(description = "Mission Rank of the ticket now; null when the ticket was deleted or the reader cannot open it",
                nullable = true, example = "M")
        MissionRank missionRank,

        @Schema(description = "Priority of the ticket now; null when the ticket was deleted or the reader cannot open it",
                nullable = true, example = "HIGH")
        TicketPriority priority
) {
}
