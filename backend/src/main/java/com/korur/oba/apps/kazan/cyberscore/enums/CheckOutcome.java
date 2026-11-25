package com.korur.oba.apps.kazan.cyberscore.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.OptionalDouble;

/**
 * Scored answer of a check, the only check value besides weight and category
 * that feeds the cyberscore.
 */
public enum CheckOutcome {

    // The check has not been performed yet.
    TODO,

    // The check passed.
    PASS,

    // The check passed in part: some of its conditions are met.
    PARTIAL,

    // The check failed.
    FAIL,

    // The check cannot be assessed because a license or subscription is missing.
    NO_LICENSE,

    // The check does not apply to the organization.
    NOT_APPLICABLE;

    /**
     * Contribution factor of this outcome to a cyberscore, from 0 to 1, or empty
     * when the outcome is left out of both the achieved and the possible weight.
     *
     * PASS gives 1.0 and PARTIAL 0.5. FAIL and TODO give 0.0, so failing and
     * pending checks lower the score. NO_LICENSE and NOT_APPLICABLE are not scored.
     *
     * @return the contribution factor, or empty when the outcome is not scorable
     */
    public OptionalDouble scoreFactor() {
        return switch (this) {
            case PASS -> OptionalDouble.of(1.0);
            case PARTIAL -> OptionalDouble.of(0.5);
            case FAIL, TODO -> OptionalDouble.of(0.0);
            case NO_LICENSE, NOT_APPLICABLE -> OptionalDouble.empty();
        };
    }
}
