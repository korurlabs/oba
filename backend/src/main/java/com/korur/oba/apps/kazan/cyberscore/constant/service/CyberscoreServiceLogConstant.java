package com.korur.oba.apps.kazan.cyberscore.constant.service;

/**
 * Log message constants for cyberscore service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CyberscoreServiceLogConstant {

    private CyberscoreServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CYBERSCORE_LOCKED = "Cyberscore row locked: organizationId={}, inserted={}";
    public static final String CYBERSCORE_RECALCULATED = "Cyberscore recalculated: organizationId={}, event={}, "
            + "scorePercentage={}, scoreLevel={}, snapshotWritten={}";
    public static final String CYBERSCORE_TREND_SKIPPED_NOT_SCORED = "Cyberscore trend skipped, organization not "
            + "scored yet: organizationId={}";
    public static final String CYBERSCORE_TREND_BUILT = "Cyberscore trend built: organizationId={}, windowStart={}, "
            + "days={}, points={}";
}
