package com.korur.oba.apps.kaizen.milestone.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal

/**
 * A milestone that another milestone depends on, with the derived conflict flag.
 */
@Schema(description = "Milestone dependency response")
public record MilestoneDependencyResponse(

        @Schema(description = "Id of the milestone depended on", example = "3f6c1d2e-8b7a-4c1e-9f0a-2b3c4d5e6f70")
        UUID milestoneId,

        @Schema(description = "Name of the milestone depended on", example = "Private beta")
        String name,

        @Schema(description = "Id of the board of the milestone depended on", example = "9a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9")
        UUID boardId,

        @Schema(description = "Target date of the milestone depended on (YYYY-MM-DD)", example = "2026-11-15")
        LocalDate targetDate,

        @Schema(description = "True when the dependent starts (or, without a start date, is due) before this target date", example = "false")
        boolean conflict
) {
}
