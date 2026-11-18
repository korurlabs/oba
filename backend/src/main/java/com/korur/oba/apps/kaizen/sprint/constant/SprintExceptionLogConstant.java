package com.korur.oba.apps.kaizen.sprint.constant;

/**
 * Log message constants for sprint domain exceptions.
 * <p>
 * These constants provide consistent exception logging messages across
 * the sprint domain, used in validators and services when errors occur.
 */
public final class SprintExceptionLogConstant {

    private SprintExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for when a sprint is not found.
     */
    public static final String SPRINT_NOT_FOUND = "Sprint not found: {}";

    /**
     * Log message for when a sprint id names a sprint of another organization.
     */
    public static final String SPRINT_OF_OTHER_ORGANIZATION =
            "Sprint is not in the organization: sprintId={}, organizationId={}";

    /**
     * Log message for when a sprint id names a sprint that was never linked to the path board.
     */
    public static final String SPRINT_NOT_ON_BOARD = "Sprint is not on the board: sprintId={}, boardId={}";

    /**
     * Log message for when linked boards of a sprint to start already have another active sprint.
     */
    public static final String SPRINT_START_BLOCKED_BY_ACTIVE_SPRINTS =
            "Sprint start blocked by active sprints on linked boards: sprintId={}, boardIds={}";

    /**
     * Log message for when a sprint status transition is invalid.
     */
    public static final String SPRINT_INVALID_STATUS_TRANSITION = "Invalid sprint status transition: sprintId={}, from={}, to={}";

    /**
     * Log message for when a sprint has no tickets to start.
     */
    public static final String SPRINT_NO_TICKETS = "Sprint has no tickets: sprintId={}";

    /**
     * Log message for when a deleted sprint is not found for restore.
     */
    public static final String SPRINT_DELETED_NOT_FOUND = "Deleted sprint not found for restore: {}";

    /**
     * Log message for when a repeating sprint has missing or invalid dates.
     */
    public static final String SPRINT_RECURRENCE_DATES_INVALID = "Repeating sprint dates invalid: sprintId={}, startDate={}, endDate={}";

    /**
     * Log message for when the days before the end date are missing or out of range.
     */
    public static final String SPRINT_RECURRENCE_DAYS_BEFORE_INVALID = "Repeating sprint days before invalid: sprintId={}, createDaysBefore={}, lengthDays={}";

    /**
     * Log message for when repetition is switched on for a completed sprint.
     */
    public static final String SPRINT_RECURRENCE_SPRINT_COMPLETED = "Cannot switch on repetition for a completed sprint: sprintId={}";

    /**
     * Log message for when the sprint was completed while a repeat settings save waited for its row.
     */
    public static final String SPRINT_RECURRENCE_SPRINT_COMPLETED_DURING_SAVE = "Sprint completed while its repeat settings were being saved: sprintId={}";

    /**
     * Log message for when a series name is blank or too long.
     */
    public static final String SPRINT_SERIES_NAME_INVALID = "Sprint series name invalid: sprintId={}, length={}";

    /**
     * Log message for when an update sends a sprint name or a series name the sprint's series does not allow.
     */
    public static final String SPRINT_SERIES_NAMING_REFUSED = "Sprint update refused by the series naming rule: sprintId={}, nameSent={}, seriesNameSent={}";

    /**
     * Log message for when a complete request asks to move unfinished tickets to the next sprint
     * of a series, but the sprint is outside an enabled series with a live board or the request
     * names another action or target.
     */
    public static final String SPRINT_NEXT_SPRINT_UNAVAILABLE = "Move to the next sprint refused: sprintId={}, ticketAction={}, targetSprintId={}, targetBoardId={}, enabledSeries={}, liveSeriesBoard={}";

    /**
     * Log message for when a concurrent write took a sprint's slug on one of its boards
     * between the free-slug check and the save.
     */
    public static final String SPRINT_SLUG_RACE_LOST = "Sprint slug taken on a board by a concurrent write: sprintId={}, cause={}";

    /**
     * Log message for when a concurrent write took the slug chosen for a link that a board
     * restore brings back.
     */
    public static final String SPRINT_LINK_SLUG_RACE_LOST =
            "Sprint slug taken on a restored board by a concurrent write: boardSprintId={}, slug={}, cause={}";

    /**
     * Log message for when a board sent as a child board of a new sprint is not below the
     * board the sprint is created on.
     */
    public static final String SPRINT_BOARD_NOT_DESCENDANT =
            "Board is not a descendant of the sprint board: rootBoardId={}, boardId={}";

    /**
     * Log message for when a board to add to a series already belongs to another enabled series.
     */
    public static final String SPRINT_SERIES_BOARD_TAKEN =
            "Board belongs to another enabled sprint series: boardId={}, seriesId={}, otherSeriesId={}";

    /**
     * Log message for when a concurrent write added a board to another series between the
     * check and the save.
     */
    public static final String SPRINT_SERIES_BOARD_RACE_LOST =
            "Board added to another sprint series by a concurrent write: seriesId={}, boardId={}, cause={}";
}
