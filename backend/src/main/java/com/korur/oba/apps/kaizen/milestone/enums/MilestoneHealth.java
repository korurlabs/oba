package com.korur.oba.apps.kaizen.milestone.enums;

/**
 * Health of a milestone, derived at read time from its dates and ticket progress.
 *
 * Never stored: it depends on the current date, so a stored value would go stale.
 */
public enum MilestoneHealth {

    // Progress keeps pace with the elapsed time, or the target is more than 14 days away.
    ON_TRACK,

    // The target is at most 14 days away and progress lags behind the elapsed time.
    AT_RISK,

    // The target date has passed and the milestone is still open.
    OVERDUE,

    // The milestone is closed.
    DONE
}
