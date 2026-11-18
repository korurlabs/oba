package com.korur.oba.apps.kazan.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * The stale backlog tickets on the reader's boards: the oldest first, capped, with the
 * full count so the page can say how many there are in all.
 */
@Schema(description = "Stale backlog tickets on the home page")
public record HomeStaleBacklogResponse(
        @Schema(description = "Number of stale backlog tickets, also those beyond the listed ones", example = "73")
        long total,

        @Schema(description = "The stale tickets, least recently active first, at most 50")
        List<HomeStaleTicketResponse> tickets
) {
}
