package com.korur.oba.apps.otak.outreach.constant.service;

/**
 * Log message constants for outreach service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OutreachServiceLogConstant {

    private OutreachServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String OUTREACH_CREATED = "Outreach created: id={}, organizationId={}, relationId={}, type={}, mailId={}, createdBy={}";
    public static final String OUTREACH_COMPLETED = "Outreach recorded as done: id={}, organizationId={}, type={}";
    public static final String OUTREACH_UPDATED = "Outreach updated: id={}, organizationId={}";
    public static final String OUTREACH_DELETED = "Outreach deleted: id={}, organizationId={}, mailId={}";
    public static final String OUTREACH_SEND_CLAIMED = "Outreach claimed for sending: id={}, organizationId={}, mailSenderId={}, attachments={}";
    public static final String OUTREACH_SENT = "Outreach sent: id={}, organizationId={}, mailSenderId={}";
    public static final String OUTREACH_SEND_FAILED = "Outreach send failed, marked FAILED: id={}, organizationId={}";
    public static final String OUTREACH_MARK_FAILED_FAILED = "Outreach send failed and could not be marked FAILED, it stays SENT: id={}, organizationId={}";
    public static final String LISTING_OUTREACHES = "Listing outreaches: organizationId={}, request={}";
    public static final String READING_OUTREACH = "Reading outreach: id={}, organizationId={}";
}
