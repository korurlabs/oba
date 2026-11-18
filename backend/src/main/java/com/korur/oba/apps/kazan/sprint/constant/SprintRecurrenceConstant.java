package com.korur.oba.apps.kazan.sprint.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.ZoneId;
import java.time.ZoneOffset;

// 4. Internal

/**
 * Constants for repeating sprints and their series: the time zone of every
 * rollover, the length and days-before limits, the series naming rule, run
 * messages and field names for error value maps.
 *
 * The zone is pinned rather than taken from the JVM, so a laptop in another
 * zone rolls sprints over at the same moment as production.
 */
public final class SprintRecurrenceConstant {

    private SprintRecurrenceConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Zone in which a repeating sprint's dates must fall at midnight.
     */
    public static final ZoneId ZONE_ID = ZoneOffset.UTC;

    /**
     * Name of ZONE_ID as sent to clients.
     */
    public static final String TIME_ZONE = "UTC";

    /**
     * Shortest sprint length in days a repeating sprint may have.
     */
    public static final int MIN_LENGTH_DAYS = 1;

    /**
     * Longest sprint length in days a repeating sprint may have.
     */
    public static final int MAX_LENGTH_DAYS = 365;

    /**
     * Smallest number of days before the end date to create the next sprint.
     */
    public static final int MIN_CREATE_DAYS_BEFORE = 1;

    /**
     * Longest message stored for the last run; matches the column length.
     */
    public static final int LAST_RUN_MESSAGE_MAX_LENGTH = 500;

    /**
     * Name of a sprint in an enabled series; the arguments are the series name
     * and the sprint's round, as in "Atasprint 2".
     */
    public static final String SERIES_SPRINT_NAME_FORMAT = "%s %d";

    /**
     * Splits a sprint name into a series name and a trailing round: group 1 is
     * the series name, group 2 the round of 1 to 6 digits. A name that does not
     * match is a series name whose first round is 1.
     */
    public static final String SERIES_NAME_PATTERN = "^(.*\\S)\\s+(\\d{1,6})$";

    /**
     * Longest series name, so that "{name} {round}" fits the 255-character
     * sprint name; matches the column length.
     */
    public static final int SERIES_NAME_MAX_LENGTH = 240;

    /**
     * First round of a series, and the smallest round a sprint name may carry.
     */
    public static final int SERIES_FIRST_ROUND = 1;

    /**
     * Run message when a linked board already has another active sprint; the
     * argument is the comma-separated board names.
     */
    public static final String RUN_MESSAGE_BLOCKED_FORMAT = "Another sprint is active on: %s";

    /**
     * Separator between board names in a run message.
     */
    public static final String RUN_MESSAGE_BOARD_SEPARATOR = ", ";

    /**
     * Run message when the sprint has no linked board left; the job then
     * switches the settings off.
     */
    public static final String RUN_MESSAGE_NO_BOARDS = "no boards";

    /**
     * Run message when the series has no live sprint left; the job then
     * switches the series off.
     */
    public static final String RUN_MESSAGE_NO_SPRINTS = "no sprints";

    /**
     * Run message when a user's change left the series without a running or
     * planned sprint, as when a takeover closed its only sprint; the series is
     * then switched off so it no longer holds its boards. The repair migration
     * V1_2_108 writes the same text.
     */
    public static final String RUN_MESSAGE_NO_LIVE_SPRINT = "no running or planned sprint left";

    /**
     * Run message for an unexpected failure; the arguments are the exception
     * class name and its message.
     */
    public static final String RUN_MESSAGE_FAILED_FORMAT = "%s: %s";

    /**
     * Run message when a user saved the sprint while the run was writing it;
     * the run was rolled back and the next run retries from the user's change.
     */
    public static final String RUN_MESSAGE_CONCURRENT_UPDATE =
            "The sprint was changed by another request during the run. Nothing was changed; the next run retries.";

    /**
     * Field name constant for the enabled switch.
     */
    public static final String FIELD_ENABLED = "enabled";

    /**
     * Field name constant for the creation mode.
     */
    public static final String FIELD_CREATION_MODE = "creationMode";

    /**
     * Field name constant for the days before the end date.
     */
    public static final String FIELD_CREATE_DAYS_BEFORE = "createDaysBefore";

    /**
     * Field name constant for the series name.
     */
    public static final String FIELD_SERIES_NAME = "seriesName";

    /**
     * Field name constant for the series ID.
     */
    public static final String FIELD_SERIES_ID = "seriesId";

    /**
     * Field name constant for the sprint length in days.
     */
    public static final String FIELD_LENGTH_DAYS = "lengthDays";

    /**
     * Unique constraint that lets a board belong to one series only; a save that
     * violates it lost the race for a board that was free when it was read.
     */
    public static final String SERIES_BOARD_UNIQUE_INDEX = "sprint_series_boards_board_id_uk";
}
