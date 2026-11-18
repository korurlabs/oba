package com.korur.oba.apps.kaizen.milestone.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * Request payload for linking tickets to, or unlinking them from, a milestone.
 */
@Schema(description = "Milestone tickets request")
public record MilestoneTicketsRequest(

        // IDs of the tickets to link or unlink.
        @NotNull(message = "Ticket IDs are required")
        @Size(min = 1, max = 200, message = "Ticket IDs must contain between 1 and 200 entries")
        @Schema(description = "IDs of the tickets to link or unlink", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "[\"3f6c1d2e-8b7a-4c1e-9f0a-2b3c4d5e6f70\"]")
        List<@NotNull UUID> ticketIds
) {
}
