package com.korur.oba.apps.kazan.milestone.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.milestone.enums.MilestoneHealth;

/**
 * An OPEN milestone on one of the reader's boards that is overdue or due soon, as listed
 * on the home page.
 */
@Schema(description = "Home deadline")
public record HomeDeadlineResponse(
        @Schema(description = "Milestone id")
        UUID id,

        @Schema(description = "Milestone name")
        String name,

        @Schema(description = "Target date", example = "2026-12-01")
        LocalDate targetDate,

        @Schema(description = "Milestone health")
        MilestoneHealth health,

        @Schema(description = "DONE tickets of the milestone", example = "4")
        long doneCount,

        @Schema(description = "All tickets of the milestone", example = "9")
        long totalCount,

        @Schema(description = "Bar colour as #RRGGBB", nullable = true, example = "#3B82F6")
        String color,

        @Schema(description = "Board id")
        UUID boardId,

        @Schema(description = "Board name")
        String boardName,

        @Schema(description = "Board slug, for the roadmap link", example = "korur")
        String boardSlug
) {
}
