package com.korur.oba.apps.kaizen.sprint.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.sprint.constant.SprintConstant;
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Request payload used to create a new sprint.
 */
@Schema(description = "Sprint create request")
public record SprintCreateRequest(

        // User-provided sprint name for display and lookup.
        @Schema(description = "Sprint name", nullable = false)
        @NotBlank(message = "Sprint name is required")
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String name,

        // Optional sprint goal description.
        @Schema(description = "Sprint goal", nullable = true)
        @Size(max = 1000, message = "Goal must be at most 1000 characters")
        String goal,

        // Sprint start date as an instant.
        @Schema(description = "Sprint start date", nullable = true)
        Instant startDate,

        // Sprint end date as an instant.
        @Schema(description = "Sprint end date", nullable = true)
        Instant endDate,

        // Optional "repeat automatically" settings. Null or not enabled creates no series and
        // the name is the sprint name; enabled makes the name the series name and the sprint
        // "{name} 1".
        @Schema(description = "Repeat automatically settings. When enabled, name is the series name (at most 240 "
                + "characters once trimmed) and the sprint is named \"{name} 1\"", nullable = true)
        @Valid
        SprintSeriesRequest series,

        // Optional descendant boards that share the new sprint. Null or empty links only the
        // path board; duplicates and the path board itself are ignored.
        @Schema(description = "IDs of descendant boards that also use the sprint", nullable = true)
        @Size(max = SprintConstant.MAX_CHILD_BOARDS, message = "Child board IDs must contain at most 50 entries")
        List<@NotNull UUID> childBoardIds,

        // True closes the ACTIVE sprints on the linked boards, moves their unfinished
        // tickets into the new sprint and starts it, also when nothing blocks. Null or
        // false only plans the sprint.
        @Schema(description = "Close the active sprints on the linked boards, move their unfinished tickets "
                + "into the new sprint and start it", nullable = true)
        Boolean takeOver
) {
}
