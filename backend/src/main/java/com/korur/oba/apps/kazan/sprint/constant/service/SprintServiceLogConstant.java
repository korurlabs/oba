package com.korur.oba.apps.kazan.sprint.constant.service;

/**
 * Log message constants for sprint service operations.
 * <p>
 * These constants provide consistent logging at the service layer,
 * tracking business logic operations and their outcomes.
 */
public final class SprintServiceLogConstant {

    private SprintServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String SPRINT_CREATED = "Sprint created: {}";
    public static final String SPRINT_CREATED_WITH_CHILD_BOARDS = "Sprint created with child boards. SprintId: {}, ChildBoards: {}";
    public static final String SPRINT_UPDATED = "Sprint updated: {}";
    public static final String SPRINT_DELETED = "Sprint deleted: {}";
    public static final String SPRINT_STARTED = "Sprint started: {}";
    public static final String SPRINT_TAKEOVER_COMPLETED = "Sprint took over the active sprints of its boards and started. SprintId: {}, ClosedSprints: {}, MovedTickets: {}, BacklogTickets: {}";
    public static final String SPRINT_COMPLETED = "Sprint completed: {}";
    public static final String SPRINT_TICKETS_MOVED_TO_BACKLOG = "Moved {} sprint tickets to backlog. SprintId: {}";
    public static final String SPRINT_TICKETS_MOVED_TO_SPRINT = "Moved {} sprint tickets to target sprint. SprintId: {}, TargetSprintId: {}";
    public static final String SPRINT_TICKETS_DELETED = "Soft-deleted {} tickets from sprint. SprintId: {}";
    public static final String SPRINT_RESTORED = "Sprint restored: {}";
    public static final String SPRINT_BOARD_LINK_RESTORED = "Restored soft-deleted board-sprint link. SprintId: {}, BoardId: {}";
    public static final String SPRINT_BOARD_LINK_SKIPPED = "Board already linked to sprint, skipping. SprintId: {}, BoardId: {}";
    public static final String SPRINT_BULK_LINKED = "Sprint bulk link completed. SprintId: {}, RequestedBoards: {}";
    public static final String SPRINT_SERIES_CREATED = "Sprint series created. SeriesId: {}, SprintId: {}, Round: {}";
    public static final String SPRINT_SERIES_UPDATED = "Sprint series updated. SeriesId: {}, SprintId: {}, Enabled: {}";
    public static final String SPRINT_SERIES_RENAMED = "Sprint series renamed. SeriesId: {}, RenamedSprints: {}";
    public static final String SPRINT_SERIES_RESCHEDULED = "Sprint series rescheduled after a change to one of its sprints. SeriesId: {}, NextCreationAt: {}";
    public static final String SPRINT_SERIES_BOARD_ADDED = "Board added to sprint series. SeriesId: {}, BoardId: {}";
    public static final String SPRINT_SERIES_BOARD_REMOVED = "Board removed from sprint series. SeriesId: {}, BoardId: {}";
    public static final String SPRINT_SERIES_BOARD_MOVED = "Board moved between sprint series. BoardId: {}, FromSeriesId: {}, ToSeriesId: {}";
    public static final String SPRINT_SERIES_ADVANCED_ON_COMPLETE = "Sprint series advanced on a manual complete. SeriesId: {}, NextSprintId: {}, Started: {}";
    public static final String SPRINT_SERIES_SWITCHED_OFF_ON_COMPLETE = "Sprint series switched off on a manual complete: it has no live board. SeriesId: {}";
    public static final String SPRINT_SERIES_SWITCHED_OFF_NO_LIVE_SPRINT = "Sprint series switched off: no running or planned sprint left. SeriesId: {}, SprintId: {}";
}
