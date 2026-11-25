package com.korur.oba.apps.kazan.cyberscore.constant;

/**
 * Field names and limits of the cyberscore domain.
 */
public final class CyberscoreConstant {

    private CyberscoreConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Field name constant for organization ID.
     */
    public static final String FIELD_ORGANIZATION_ID = "organizationId";

    /**
     * Query parameter of the trend window length in days.
     */
    public static final String PARAM_DAYS = "days";

    /**
     * Shortest trend window: today only.
     */
    public static final int MIN_TREND_DAYS = 1;

    /**
     * Longest trend window, one year, which bounds the snapshots one trend read loads.
     */
    public static final int MAX_TREND_DAYS = 365;

    /**
     * Trend window used when the client names none. A String, because a
     * request parameter default takes only a compile-time String.
     */
    public static final String DEFAULT_TREND_DAYS = "30";
}
