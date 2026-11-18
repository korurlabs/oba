package com.korur.oba.apps.kazan.sprint.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

/**
 * What starting a PLANNED sprint would close: the boards whose ACTIVE sprint blocks the
 * start, and whether the caller may close them with a takeover.
 */
@Schema(description = "Preview of the active sprints a sprint start would close")
public record SprintStartPreviewResponse(

        // False when a board linked to a blocking sprint is not managed by the caller, including
        // boards the caller cannot see and that are therefore not listed.
        @Schema(description = "Whether the caller is ADMIN or OWNER of every board linked to every blocking "
                + "sprint, including boards not listed", nullable = false)
        boolean canTakeOver,

        // Empty when nothing blocks, so a plain start works.
        @Schema(description = "One entry per board the caller is a member of that is linked to a blocking "
                + "sprint, in the link order of the sprint being started; empty when nothing blocks",
                nullable = false)
        List<SprintStartConflictResponse> conflicts
) {
}
