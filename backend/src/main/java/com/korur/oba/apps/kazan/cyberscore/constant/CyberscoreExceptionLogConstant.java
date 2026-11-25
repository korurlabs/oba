package com.korur.oba.apps.kazan.cyberscore.constant;

/**
 * Log message constants for cyberscore exception paths.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CyberscoreExceptionLogConstant {

    private CyberscoreExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for a score row that is missing where the scoring lock should have created it.
     */
    public static final String CYBERSCORE_ROW_MISSING = "Cyberscore row missing for organization {}: "
            + "the scoring lock was not taken before the recalculation";
}
