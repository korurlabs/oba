package com.korur.oba.apps.kazan.sprint.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

import com.korur.oba.apps.kazan.sprint.enums.SprintStatus;

/**
 * Response payload for listing sprints.
 */
@Schema(description = "Sprint list response")
public record SprintListResponse(

        // Unique sprint identifier.
        @Schema(description = "Sprint id", nullable = false)
        UUID id,

        // Sprint name shown in UI.
        @Schema(description = "Sprint name", nullable = false)
        String name,

        // URL segment of the sprint on the board of the request path.
        @Schema(description = "URL slug of the sprint on the board of the request path, unique among the board's sprints",
                nullable = false, example = "sensey-sprint-5")
        String slug,

        // Current sprint status.
        @Schema(description = "Sprint status", nullable = false)
        SprintStatus status,

        // Sprint creation timestamp; orders sprints by creation.
        @Schema(description = "Created at", nullable = false, example = "2026-10-20T08:15:00Z")
        Instant createdAt,

        // Sprint start date as an instant.
        @Schema(description = "Sprint start date", nullable = true)
        Instant startDate,

        // Sprint end date as an instant.
        @Schema(description = "Sprint end date", nullable = true)
        Instant endDate,

        // Set only on an ACTIVE sprint whose closing leaves its repeating series nothing to run.
        @Schema(description = "Name of the repeating series that stops when this ACTIVE sprint is closed, "
                + "because the series has no other running or planned sprint; null otherwise",
                nullable = true, example = "Sprint")
        String endsSeriesName
) {
}
