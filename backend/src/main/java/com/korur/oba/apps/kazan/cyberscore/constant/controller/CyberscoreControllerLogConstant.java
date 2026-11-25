package com.korur.oba.apps.kazan.cyberscore.constant.controller;

/**
 * Log message constants for cyberscore controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CyberscoreControllerLogConstant {

    private CyberscoreControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CYBERSCORE_GET_REQUEST = "Received request to get the cyberscore";
    public static final String CYBERSCORE_GET_COMPLETED = "Get cyberscore request completed";

    public static final String CYBERSCORE_TREND_REQUEST = "Received request to get the cyberscore trend";
    public static final String CYBERSCORE_TREND_COMPLETED = "Get cyberscore trend request completed";
}
