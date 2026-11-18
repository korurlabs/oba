package com.korur.oba.apps.kaizen.milestone.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.milestone.enums.MilestoneStatus;

/**
 * Query parameters for listing the milestones of a board.
 */
@Getter
@Setter
@NoArgsConstructor
public class MilestoneQueryRequest {

    /**
     * Filter by status. Null means all statuses.
     */
    @Schema(description = "Filter by milestone status", nullable = true, example = "OPEN")
    private MilestoneStatus status;

    /**
     * Include the milestones of the parent, child and sibling boards the user is a member of.
     */
    @Schema(description = "Include milestones of related boards the user is a member of", example = "false")
    private boolean includeRelatedBoards;
}
