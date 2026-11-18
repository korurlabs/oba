package com.korur.oba.apps.otak.mailtemplate.constant.controller;

/**
 * Response message constants for mail template controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MailTemplateControllerResponseConstant {

    private MailTemplateControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_MAIL_TEMPLATE_LIST_RETRIEVED = "Mail templates retrieved successfully";
    public static final String RESPONSE_MAIL_TEMPLATE_CREATED = "Mail template created successfully";
    public static final String RESPONSE_MAIL_TEMPLATE_RETRIEVED = "Mail template retrieved successfully";
    public static final String RESPONSE_MAIL_TEMPLATE_UPDATED = "Mail template updated successfully";
    public static final String RESPONSE_MAIL_TEMPLATE_DELETED = "Mail template deleted successfully";
}
