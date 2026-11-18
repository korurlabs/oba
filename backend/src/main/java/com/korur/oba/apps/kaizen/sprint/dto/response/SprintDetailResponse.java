package com.korur.oba.apps.kaizen.sprint.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.korur.oba.apps.kaizen.board.dto.response.BoardListResponse;
import com.korur.oba.apps.kaizen.sprint.enums.SprintStatus;

/**
 * Response payload for a detailed sprint view.
 */
@Schema(description = "Sprint detail response")
public record SprintDetailResponse(

        // Unique sprint identifier.
        @Schema(description = "Sprint id", nullable = false)
        UUID id,

        // Sprint creation timestamp.
        @Schema(description = "Created at", nullable = false)
        Instant createdAt,

        // Sprint update timestamp.
        @Schema(description = "Updated at", nullable = false)
        Instant updatedAt,

        // Sprint name shown in UI.
        @Schema(description = "Sprint name", nullable = false)
        String name,

        // Sprint goal description.
        @Schema(description = "Sprint goal", nullable = true)
        String goal,

        // Sprint start date as an instant.
        @Schema(description = "Sprint start date", nullable = true)
        Instant startDate,

        // Sprint end date as an instant.
        @Schema(description = "Sprint end date", nullable = true)
        Instant endDate,

        // Current sprint status.
        @Schema(description = "Sprint status", nullable = false)
        SprintStatus status,

        // Place of the sprint in its series; null when it has no series.
        @Schema(description = "Round within the sprint's series; null when it has no series", nullable = true,
                example = "2")
        Integer round,

        // Board identifier this sprint belongs to.
        @Schema(description = "Boards", nullable = false)
        List<BoardListResponse> boards,

        // Series the sprint belongs to, with its settings and job state; null when it has none.
        @Schema(description = "Series of the sprint; null when it has none", nullable = true)
        SprintSeriesResponse series
) {
}
