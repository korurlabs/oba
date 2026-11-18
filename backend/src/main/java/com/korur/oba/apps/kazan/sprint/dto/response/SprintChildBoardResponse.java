package com.korur.oba.apps.kazan.sprint.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

/**
 * A descendant board that a new sprint of the parent board can also be linked to.
 */
@Schema(description = "Descendant board offered when a sprint is created on its ancestor board")
public record SprintChildBoardResponse(

        // Board identifier, sent back in the create request's childBoardIds.
        @Schema(description = "Board id", nullable = false)
        UUID id,

        // Board name shown in the list.
        @Schema(description = "Board name", nullable = false)
        String name,

        // Board ticket prefix shown next to the name.
        @Schema(description = "Board prefix", nullable = false, example = "KOR")
        String prefix,

        // Board color, if set.
        @Schema(description = "Board color as a hex value", nullable = true, example = "#3B82F6")
        String color,

        // Direct parent, so the client can rebuild the tree. Null when the caller is not a member
        // of the parent, so that the id of a board the caller cannot see is never exposed. A direct
        // child of the board of the request path always carries it, since the caller can see that board.
        @Schema(description = "Id of the board's direct parent; null when the caller is not a member of the parent",
                nullable = true)
        UUID parentBoardId,

        // Distance from the board of the request path.
        @Schema(description = "Depth below the board of the request path; 1 is a direct child",
                nullable = false, example = "1")
        int depth,

        // True when the caller is ADMIN or OWNER of this board, which the create request needs.
        @Schema(description = "Whether the caller is ADMIN or OWNER of the board and so may link a sprint to it",
                nullable = false)
        boolean canLink,

        // Active sprint of the board, if it has one.
        @Schema(description = "Id of the board's active sprint", nullable = true)
        UUID activeSprintId,

        // Name of the active sprint, if the board has one.
        @Schema(description = "Name of the board's active sprint", nullable = true)
        String activeSprintName,

        // Set when closing the active sprint leaves its repeating series nothing to run.
        @Schema(description = "Name of the repeating series that stops when the board's active sprint is "
                + "closed, because the series has no other running or planned sprint; null when nothing stops",
                nullable = true, example = "Sprint")
        String activeSprintEndsSeriesName
) {
}
