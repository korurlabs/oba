package com.korur.oba.apps.kazan.dailyupdate.constant.listener;

/**
 * Log message constants for daily update event listeners.
 */
public final class DailyUpdateListenerLogConstant {

    private DailyUpdateListenerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String DAILY_UPDATE_NUDGE_PUSH_DISPATCHED = "Daily update nudge push dispatched: {}. Recipient: {}";
    public static final String DAILY_UPDATE_NUDGE_PUSH_SKIPPED_WITHOUT_KAZAN = "Daily update nudge push skipped, the recipient has no Kazan access: {}. Recipient: {}";
}
