package com.korur.oba.apps.kazan.milestone.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.milestone.enums.MilestoneHealth;
import com.korur.oba.apps.kazan.milestone.enums.MilestoneStatus;

/**
 * Response payload for a milestone in a list or a roadmap lane.
 */
@Schema(description = "Milestone list response")
public record MilestoneListResponse(

        @Schema(description = "Milestone id", example = "3f6c1d2e-8b7a-4c1e-9f0a-2b3c4d5e6f70")
        UUID id,

        @Schema(description = "Milestone name", example = "Public beta")
        String name,

        @Schema(description = "Milestone status", example = "OPEN")
        MilestoneStatus status,

        @Schema(description = "Start date (YYYY-MM-DD)", nullable = true, example = "2026-10-01")
        LocalDate startDate,

        @Schema(description = "Target date (YYYY-MM-DD)", example = "2026-12-15")
        LocalDate targetDate,

        @Schema(description = "Id of the board the milestone belongs to", example = "9a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9")
        UUID boardId,

        @Schema(description = "Name of the board the milestone belongs to", example = "Kagi")
        String boardName,

        @Schema(description = "Ticket key prefix of the board the milestone belongs to", example = "KAGI")
        String boardPrefix,

        @Schema(description = "Attached non-deleted tickets with status DONE", example = "12")
        long doneCount,

        @Schema(description = "Attached non-deleted tickets, sub-tickets included", example = "20")
        long totalCount,

        @Schema(description = "Health derived from dates and progress", example = "ON_TRACK")
        MilestoneHealth health,

        @Schema(description = "Bar colour as #RRGGBB", nullable = true, example = "#3B82F6")
        String color,

        @Schema(description = "Live milestones on boards the user is a member of that this milestone depends on")
        List<MilestoneDependencyResponse> dependsOn
) {
}
