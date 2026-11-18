package com.korur.oba.apps.kaizen.sprint.dto.request;

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
 * Request payload for bulk-linking a sprint to multiple boards in one operation.
 */
@Schema(description = "Sprint bulk link request")
public record SprintBulkLinkRequest(

        // IDs of the boards to link to the sprint.
        @NotNull(message = "Board IDs are required")
        @Size(min = 1, max = 50, message = "Board IDs must contain between 1 and 50 entries")
        @Schema(description = "IDs of the boards to link to the sprint",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotNull UUID> boardIds
) {
}
