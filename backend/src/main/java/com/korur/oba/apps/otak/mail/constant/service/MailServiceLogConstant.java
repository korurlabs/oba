package com.korur.oba.apps.otak.mail.constant.service;

/**
 * Log message constants for mail service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MailServiceLogConstant {

    private MailServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String MAIL_CREATED = "Mail created: id={}, organizationId={}, relationId={}";
    public static final String MAIL_COPIED_FROM_TEMPLATE =
            "Mail copied from template: id={}, mailTemplateId={}, organizationId={}, relationId={}";
    public static final String MAIL_UPDATED = "Mail updated: id={}, organizationId={}";
    public static final String MAIL_DELETED = "Mail deleted: id={}, organizationId={}";
}
