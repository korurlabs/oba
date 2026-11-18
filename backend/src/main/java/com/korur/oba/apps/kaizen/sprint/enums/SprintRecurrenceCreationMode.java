package com.korur.oba.apps.kaizen.sprint.enums;

/**
 * Defines when the next sprint of a repeating sprint is created.
 */
public enum SprintRecurrenceCreationMode {

    // The next sprint is created at the rollover on the end date.
    AT_END,

    // The next sprint is created as PLANNED a set number of days before the end date.
    DAYS_BEFORE
}
