package com.korur.oba.apps.kazan.cyberscore.constant.service;

/**
 * Log message constants for check service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CheckServiceLogConstant {

    private CheckServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CHECK_CREATED = "Check created: id={}, organizationId={}, weight={}, category={}, "
            + "outcome={}, status={}";
    public static final String CHECK_UPDATED = "Check updated: id={}, organizationId={}, scoreEvent={}";
    public static final String CHECK_OUTCOME_SET = "Check outcome set: id={}, organizationId={}, outcome={}, "
            + "status={}, changed={}";
    public static final String CHECK_STATUS_SET = "Check status set: id={}, organizationId={}, status={}";
    public static final String CHECK_DELETED = "Check deleted: id={}, organizationId={}";
}
