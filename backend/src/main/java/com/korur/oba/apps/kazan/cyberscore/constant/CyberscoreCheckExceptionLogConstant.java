package com.korur.oba.apps.kazan.cyberscore.constant;

/**
 * Log message constants for check exception paths.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CyberscoreCheckExceptionLogConstant {

    private CyberscoreCheckExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for a check the organization does not have.
     */
    public static final String CHECK_NOT_FOUND = "Check not found: checkId={}, organizationId={}";

    /**
     * Log message for a name another check of the organization already has.
     */
    public static final String CHECK_NAME_DUPLICATE = "Check name already taken: name={}, organizationId={}";

    /**
     * Log message for a name that a concurrent write took after the name check.
     */
    public static final String CHECK_NAME_RACE_LOST = "Check name taken by a concurrent write: name={}, "
            + "organizationId={}, cause={}";
}
