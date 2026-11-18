package com.korur.oba.apps.kazan.ticket.dto.response;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.sprint.enums.SprintStatus;

/**
 * Embedded sprint info for the all-tickets listing.
 */
@Schema(description = "Ticket sprint")
public record TicketSprintResponse(
        @Schema(description = "Sprint id", nullable = false)
        UUID id,

        @Schema(description = "Sprint name", nullable = false)
        String name,

        @Schema(description = "Sprint status", nullable = false)
        SprintStatus status
) {
}
