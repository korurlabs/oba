package com.korur.oba.apps.kaizen.sprint.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.apps.kaizen.sprint.constant.SprintRecurrenceConstant;
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Request payload used to update sprint metadata.
 */
@Schema(description = "Sprint update request")
public record SprintUpdateRequest(

        // Updated sprint name if provided.
        @Schema(description = "Sprint name", nullable = true)
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String name,

        // Updated sprint goal if provided.
        @Schema(description = "Sprint goal", nullable = true)
        @Size(max = 1000, message = "Goal must be at most 1000 characters")
        String goal,

        // Updated sprint start date if provided.
        @Schema(description = "Sprint start date", nullable = true)
        Instant startDate,

        // Updated sprint end date if provided.
        @Schema(description = "Sprint end date", nullable = true)
        Instant endDate,

        // New name of the sprint's series; renames its unfinished sprints. Only for a sprint
        // of an enabled series, which then takes no name.
        @Schema(description = "New series name; renames the unfinished sprints of the series. Only for a sprint "
                + "of an enabled series, which then takes no name", nullable = true, example = "Korursprint")
        @Size(max = SprintRecurrenceConstant.SERIES_NAME_MAX_LENGTH, message = "Series name must be at most 240 characters")
        String seriesName
) {
}
