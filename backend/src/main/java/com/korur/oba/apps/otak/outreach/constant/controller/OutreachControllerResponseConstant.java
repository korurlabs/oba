package com.korur.oba.apps.otak.outreach.constant.controller;

/**
 * Response message constants for outreach controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OutreachControllerResponseConstant {

    private OutreachControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_OUTREACH_LIST_RETRIEVED = "Outreaches retrieved successfully";
    public static final String RESPONSE_OUTREACH_CREATED = "Outreach created successfully";
    public static final String RESPONSE_OUTREACH_RETRIEVED = "Outreach retrieved successfully";
    public static final String RESPONSE_OUTREACH_UPDATED = "Outreach updated successfully";
    public static final String RESPONSE_OUTREACH_SENT = "Outreach sent successfully";
    public static final String RESPONSE_OUTREACH_COMPLETED = "Outreach recorded as done successfully";
    public static final String RESPONSE_OUTREACH_DELETED = "Outreach deleted successfully";
}
