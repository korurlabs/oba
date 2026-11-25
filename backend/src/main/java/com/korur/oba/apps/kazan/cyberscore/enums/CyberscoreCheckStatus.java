package com.korur.oba.apps.kazan.cyberscore.enums;

/**
 * Workflow status of a check.
 *
 * Workflow only: the cyberscore calculation never reads it. The scored answer
 * is {@link CyberscoreCheckOutcome}.
 */
public enum CyberscoreCheckStatus {

    // Work on the check has not begun.
    NOT_STARTED,

    // Someone is working on the check.
    IN_PROGRESS,

    // Work on the check is finished.
    DONE,

    // The check does not apply to the organization, as far as the workflow goes.
    NOT_APPLICABLE;

    /**
     * The status a check gets with an outcome: {@link CyberscoreCheckOutcome#TODO}
     * has not been started, {@link CyberscoreCheckOutcome#NOT_APPLICABLE} stays
     * not applicable, and any answered outcome is done.
     *
     * @param outcome the check's outcome
     * @return the status that outcome implies
     */
    public static CyberscoreCheckStatus fromOutcome(CyberscoreCheckOutcome outcome) {
        return switch (outcome) {
            case TODO -> NOT_STARTED;
            case NOT_APPLICABLE -> NOT_APPLICABLE;
            case PASS, PARTIAL, FAIL, NO_LICENSE -> DONE;
        };
    }
}
