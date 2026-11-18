package com.korur.oba.apps.kaizen.milestone.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * One roadmap lane: a board and its milestones, sorted by target date.
 */
@Schema(description = "Roadmap board lane")
public record RoadmapBoardResponse(

        @Schema(description = "Board id", example = "9a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9")
        UUID id,

        @Schema(description = "Board name", example = "Kagi")
        String name,

        @Schema(description = "Board ticket key prefix", example = "KAGI")
        String prefix,

        @Schema(description = "Board colour", nullable = true, example = "#3B82F6")
        String color,

        @Schema(description = "Milestones of the board in the window, sorted by target date")
        List<MilestoneListResponse> milestones
) {
}
