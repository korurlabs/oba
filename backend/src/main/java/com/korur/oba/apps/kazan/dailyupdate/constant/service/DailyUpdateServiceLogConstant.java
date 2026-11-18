package com.korur.oba.apps.kazan.dailyupdate.constant.service;

/**
 * Log message constants for daily update service operations.
 */
public final class DailyUpdateServiceLogConstant {

    private DailyUpdateServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String DAILY_UPDATE_CREATED = "Daily update created: {}. Lines: {}, progress synced: {}";
    public static final String DAILY_UPDATE_EDITED = "Daily update edited: {}. Lines: {}, progress synced: {}";
    public static final String DAILY_UPDATE_DELETED = "Daily update deleted: {}";
    public static final String DAILY_UPDATE_TICKET_PROGRESS_SYNCED = "Daily update {} set progress of ticket {} to {}";
    public static final String DAILY_UPDATE_NUDGE_CREATED = "Daily update nudge sent: {}. Organization: {}, recipient: {}, day: {}";
}
