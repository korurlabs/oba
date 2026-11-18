package com.korur.oba.apps.otak.mail.constant;

/**
 * Log message constants for mail domain exceptions.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MailExceptionLogConstant {

    private MailExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for a mail that the organization does not have, missing or of another organization.
     */
    public static final String MAIL_NOT_FOUND = "Mail not found: id={}, organizationId={}";

    /**
     * Log message for a mail id that no organization has, in a lookup that does not know the organization yet.
     */
    public static final String MAIL_NOT_FOUND_BY_ID = "Mail not found: id={}";

    /**
     * Log message for a change of the attachments of a mail whose outreach was sent or is being sent.
     */
    public static final String MAIL_SENT = "Mail attachment change refused, outreach sent: mailId={}, organizationId={}";
}
