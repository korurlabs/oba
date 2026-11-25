package com.korur.oba.apps.kazan.cyberscore.enums;

/**
 * Workflow status of a check.
 *
 * Workflow only: the cyberscore calculation never reads it. The scored answer
 * is {@link CheckOutcome}.
 */
public enum CheckStatus {

    // Work on the check has not begun.
    NOT_STARTED,

    // Someone is working on the check.
    IN_PROGRESS,

    // Work on the check is finished.
    DONE,

    // The check does not apply to the organization, as far as the workflow goes.
    NOT_APPLICABLE
}
