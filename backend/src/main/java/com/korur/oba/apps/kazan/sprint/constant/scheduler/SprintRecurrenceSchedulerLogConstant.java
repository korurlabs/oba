package com.korur.oba.apps.kazan.sprint.constant.scheduler;

/**
 * Log message constants for the sprint recurrence scheduler.
 */
public final class SprintRecurrenceSchedulerLogConstant {

    private SprintRecurrenceSchedulerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String SPRINT_RECURRENCE_RUN_STARTED = "Sprint recurrence run started. DueCount: {}, Now: {}";
    public static final String SPRINT_RECURRENCE_RUN_FAILED = "Sprint recurrence run failed. SeriesId: {}";
    public static final String SPRINT_RECURRENCE_RUN_CONFLICT = "Sprint recurrence run rolled back: the sprint was changed by another request; the next run retries. SeriesId: {}, Cause: {}";
    public static final String SPRINT_RECURRENCE_FAILURE_NOT_RECORDED = "Could not record a failed sprint series run. SeriesId: {}";
    public static final String SPRINT_RECURRENCE_STARTUP_RUN_FAILED = "Sprint recurrence run at application start failed";
}
