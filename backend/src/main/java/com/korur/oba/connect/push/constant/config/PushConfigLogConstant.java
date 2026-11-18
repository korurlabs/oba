package com.korur.oba.connect.push.constant.config;

/**
 * Log message constants of the push configuration.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PushConfigLogConstant {

    private PushConfigLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String PUSH_ENABLED = "Push sending through Expo is on";
    public static final String PUSH_DISABLED =
            "Push sending is off: oba.integrations.expo.access-token (KAIZEN_EXPO_ACCESS_TOKEN) is blank";
    public static final String EXECUTOR_REJECTED = "Push dispatch dropped, the push executor queue is full: queueCapacity={}";
}
