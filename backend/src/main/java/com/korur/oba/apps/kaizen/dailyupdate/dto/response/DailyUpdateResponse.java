package com.korur.oba.apps.kaizen.dailyupdate.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.dto.response.TicketCreatedByResponse;

/**
 * Response payload for a daily update in the feed or on its own.
 */
@Schema(description = "Daily update response")
public record DailyUpdateResponse(

        @Schema(description = "Daily update id", example = "5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d")
        UUID id,

        @Schema(description = "Creation time (UTC)", example = "2026-10-19T08:15:00Z")
        Instant createdAt,

        @Schema(description = "Time of the author's last edit (UTC); null when never edited", nullable = true,
                example = "2026-10-19T09:02:00Z")
        Instant editedAt,

        @Schema(description = "Author of the update")
        TicketCreatedByResponse author,

        @Schema(description = "What the author will focus on today", nullable = true, example = "Finish the mobile menu design")
        String focus,

        @Schema(description = "Questions to colleagues", nullable = true, example = "Who reviews the icon set?")
        String questions,

        @Schema(description = "Ticket lines in display order")
        List<DailyUpdateTicketResponse> items,

        @Schema(description = "Tickets of the author that are DONE in an ACTIVE sprint, on boards the reader is a member of, by key")
        List<DailyUpdateFinishedTicketResponse> finishedTickets,

        @Schema(description = "Plain text of the update in the team's four-part format, the same for every reader",
                example = "1. Which ticket am I working on?\n   - ACME-218: Mobile Menu Figma Design")
        String summaryText
) {
}
