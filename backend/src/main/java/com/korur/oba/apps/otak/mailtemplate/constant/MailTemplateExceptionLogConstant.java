package com.korur.oba.apps.otak.mailtemplate.constant;

/**
 * Log message constants for mail template domain exceptions.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MailTemplateExceptionLogConstant {

    private MailTemplateExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for a mail template that the organization does not have, missing or of another organization.
     */
    public static final String MAIL_TEMPLATE_NOT_FOUND = "Mail template not found: id={}, organizationId={}";

    /**
     * Log message for a name and language another mail template of the organization already has, case ignored.
     */
    public static final String MAIL_TEMPLATE_NAME_TAKEN =
            "Mail template name refused, already taken: organizationId={}, name={}, language={}";
}
