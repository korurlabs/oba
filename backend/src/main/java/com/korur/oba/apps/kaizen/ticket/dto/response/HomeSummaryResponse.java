package com.korur.oba.apps.kaizen.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

/**
 * Open work counts shown on the home page, across the boards the user is a member of.
 */
@Schema(description = "Home page summary of open work")
public record HomeSummaryResponse(
        @Schema(description = "Incident tickets that are not DONE", example = "3")
        long openIncidents,

        @Schema(description = "Tickets that are not DONE in the active sprints of the user's boards", example = "17")
        long openSprintTickets
) {
}
