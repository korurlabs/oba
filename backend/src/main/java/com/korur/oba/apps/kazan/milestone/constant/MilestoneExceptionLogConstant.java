package com.korur.oba.apps.kazan.milestone.constant;

/**
 * Log message constants for milestone domain exceptions.
 */
public final class MilestoneExceptionLogConstant {

    private MilestoneExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for when a milestone is not found or not visible to the user.
     */
    public static final String MILESTONE_NOT_FOUND = "Milestone not found: {}";

    /**
     * Log message for when the start date is after the target date.
     */
    public static final String MILESTONE_START_AFTER_TARGET = "Milestone start date after target date: startDate={}, targetDate={}";

    /**
     * Log message for when a milestone is already in the requested status.
     */
    public static final String MILESTONE_STATUS_UNCHANGED = "Milestone already has status: milestoneId={}, status={}";

    /**
     * Log message for when a milestone is restored but is not deleted.
     */
    public static final String MILESTONE_NOT_DELETED = "Milestone is not deleted: {}";

    /**
     * Log message for when a ticket's board is not related to the milestone's board.
     */
    public static final String MILESTONE_NOT_LINKABLE = "Milestone not linkable: milestoneId={}, ticketBoardId={}";

    /**
     * Log message for when the roadmap window is inverted.
     */
    public static final String ROADMAP_FROM_AFTER_TO = "Roadmap from date after to date: from={}, to={}";

    /**
     * Log message for when a milestone is made to depend on itself.
     */
    public static final String MILESTONE_DEPENDENCY_SELF = "Milestone cannot depend on itself: {}";

    /**
     * Log message for when a dependency would close a cycle.
     */
    public static final String MILESTONE_DEPENDENCY_CYCLE = "Milestone dependency would create a cycle: milestoneId={}, dependsOnMilestoneId={}";

    /**
     * Log message for when the boards of two milestones are not related.
     */
    public static final String MILESTONE_DEPENDENCY_NOT_RELATED = "Milestone dependency between unrelated boards: milestoneId={}, dependsOnMilestoneId={}";

    /**
     * Log message for when a dependency to remove does not exist.
     */
    public static final String MILESTONE_DEPENDENCY_NOT_FOUND = "Milestone dependency not found: milestoneId={}, dependsOnMilestoneId={}";
}
