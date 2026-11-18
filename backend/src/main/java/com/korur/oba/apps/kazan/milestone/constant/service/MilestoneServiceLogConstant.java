package com.korur.oba.apps.kazan.milestone.constant.service;

/**
 * Log message constants for milestone service operations.
 */
public final class MilestoneServiceLogConstant {

    private MilestoneServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String MILESTONE_CREATED = "Milestone created: {}";
    public static final String MILESTONE_UPDATED = "Milestone updated: {}";
    public static final String MILESTONE_CLOSED = "Milestone closed: {}";
    public static final String MILESTONE_REOPENED = "Milestone reopened: {}";
    public static final String MILESTONE_DELETED = "Milestone deleted: {}. Detached tickets: {}";
    public static final String MILESTONE_RESTORED = "Milestone restored: {}";
    public static final String MILESTONE_TICKETS_LINKED = "Linked {} tickets to milestone {}";
    public static final String MILESTONE_TICKETS_UNLINKED = "Unlinked {} tickets from milestone {}";
    public static final String BOARD_MILESTONES_DELETED = "Soft-deleted {} milestones of board {}. Detached tickets: {}";
    public static final String MILESTONE_DEPENDENCY_ADDED = "Milestone {} now depends on milestone {}";
    public static final String MILESTONE_DEPENDENCY_EXISTS = "Milestone {} already depends on milestone {}";
    public static final String MILESTONE_DEPENDENCY_REMOVED = "Milestone {} no longer depends on milestone {}";
    public static final String BOARD_MILESTONES_RESTORED = "Restored {} milestones of board {}";
}
