package com.korur.oba.apps.kaizen.sprint.constant.processor;

/**
 * Log message constants for the sprint recurrence processor, which runs the sprint series.
 */
public final class SprintRecurrenceProcessorLogConstant {

    private SprintRecurrenceProcessorLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String SPRINT_RECURRENCE_ROLLED_OVER = "Sprint rolled over. SprintId: {}, NextSprintId: {}, RolloverAt: {}, UnfinishedTickets: {}";
    public static final String SPRINT_RECURRENCE_STARTED = "Planned repeating sprint started. SprintId: {}";
    public static final String SPRINT_RECURRENCE_BLOCKED = "Sprint recurrence blocked by another active sprint. SprintId: {}, Boards: {}";
    public static final String SPRINT_RECURRENCE_DAYS_BEFORE_DOES_NOT_FIT = "Days before does not fit the sprint length; the next sprint is created at the end date instead. SprintId: {}, CreateDaysBefore: {}, LengthDays: {}";
    public static final String SPRINT_SERIES_LOCKED_ELSEWHERE = "Sprint series locked by another run, skipping. SeriesId: {}";
    public static final String SPRINT_SERIES_NOT_DUE = "Sprint series no longer due, skipping. SeriesId: {}";
    public static final String SPRINT_SERIES_NO_BOARDS = "Sprint series switched off: it has no live board. SeriesId: {}";
    public static final String SPRINT_SERIES_NO_SPRINTS = "Sprint series switched off: it has no live sprint. SeriesId: {}";
    public static final String SPRINT_SERIES_SPRINT_CREATED = "Sprint series created its next sprint. SeriesId: {}, SprintId: {}, Round: {}";
    public static final String SPRINT_SERIES_FAILURE_RECORDED = "Sprint series run recorded as failed. SeriesId: {}";
}
