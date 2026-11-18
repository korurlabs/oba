package com.korur.oba.apps.kaizen.dailyupdate.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * Response payload for a daily update preview: everything the posted card of
 * the request would show, so one answer renders the whole preview card. Nothing
 * is saved.
 */
@Schema(description = "Daily update preview response")
public record DailyUpdatePreviewResponse(

        @Schema(description = "Ticket lines in display order")
        List<DailyUpdateTicketResponse> items,

        @Schema(description = "What the author will focus on today", nullable = true, example = "Finish the mobile menu design")
        String focus,

        @Schema(description = "Questions to colleagues", nullable = true, example = "Who reviews the icon set?")
        String questions,

        @Schema(description = "Tickets of the author that are DONE in an ACTIVE sprint, on boards the author is a member of, by key")
        List<DailyUpdateFinishedTicketResponse> finishedTickets,

        @Schema(description = "Plain text of the update in the team's four-part format",
                example = "1. Which ticket am I working on?\n   - ACME-218: Mobile Menu Figma Design")
        String summaryText
) {
}
