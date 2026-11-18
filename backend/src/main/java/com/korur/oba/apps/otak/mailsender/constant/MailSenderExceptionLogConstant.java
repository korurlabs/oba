package com.korur.oba.apps.otak.mailsender.constant;

/**
 * Log message constants for mail sender domain exceptions.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MailSenderExceptionLogConstant {

    private MailSenderExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for a mail sender that the organization does not have, missing or of another organization.
     */
    public static final String MAIL_SENDER_NOT_FOUND = "Mail sender not found: id={}, organizationId={}";

    /**
     * Log message for a mail address another sender of the organization already has, case ignored.
     */
    public static final String MAIL_SENDER_TAKEN =
            "Mail sender refused, address already taken: organizationId={}";

    /**
     * Log message for a mail address that names no account of the Microsoft 365 tenant.
     */
    public static final String MAILBOX_NOT_IN_TENANT = "Mail sender refused, no mailbox in the tenant: organizationId={}";

    /**
     * Log message for a mail address whose Microsoft 365 account has no mailbox.
     */
    public static final String ACCOUNT_WITHOUT_MAILBOX =
            "Mail sender refused, the tenant account has no mailbox: organizationId={}";

    /**
     * Log message for a mail address that names no shared mailbox among the member accounts of the tenant.
     */
    public static final String SHARED_MAILBOX_NOT_IN_TENANT =
            "Mail sender refused, no shared mailbox in the tenant: organizationId={}";
}
