package com.korur.oba.connect.push.constant.service;

/**
 * Log message constants for push preference service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PushPreferenceServiceLogConstant {

    private PushPreferenceServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String PREFERENCES_UPDATED = "Push preferences updated: userId={}, mentionsAndReplies={}, "
            + "everyone={}, here={}, urgentIncidents={}, showPreviews={}";
}
