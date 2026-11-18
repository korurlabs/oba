package com.korur.oba.apps.kaizen.sprint.enums;

/**
 * Defines the outcome of the last automatic run on a repeating sprint.
 */
public enum SprintRecurrenceRunStatus {

    // The run did its work.
    OK,

    // The run changed nothing because a linked board has another active sprint; it retries.
    BLOCKED,

    // The run changed nothing because of an error; it retries.
    FAILED
}
