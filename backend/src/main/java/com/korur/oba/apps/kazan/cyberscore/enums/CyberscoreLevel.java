package com.korur.oba.apps.kazan.cyberscore.enums;

/**
 * Risk band of an organization's cyberscore.
 *
 * Each band covers a contiguous percentage range, except UNKNOWN, which marks
 * an organization without scorable checks.
 */
public enum CyberscoreLevel {

    // No scorable checks yet.
    UNKNOWN,

    // 0 to 20 percent.
    CRITICAL,

    // 21 to 40 percent.
    HIGH,

    // 41 to 60 percent.
    MEDIUM,

    // 61 to 80 percent.
    LOW,

    // 81 to 100 percent.
    MINIMAL;

    /**
     * Maps a score percentage to its band. Never returns {@link #UNKNOWN}: the
     * caller decides that from whether any check was scorable.
     *
     * @param scorePercentage the score percentage, from 0 to 100
     * @return the band of the percentage
     */
    public static CyberscoreLevel fromPercentage(int scorePercentage) {
        if (scorePercentage <= 20) {
            return CRITICAL;
        }
        if (scorePercentage <= 40) {
            return HIGH;
        }
        if (scorePercentage <= 60) {
            return MEDIUM;
        }
        if (scorePercentage <= 80) {
            return LOW;
        }
        return MINIMAL;
    }
}
