package com.korur.oba.apps.otak.mailtemplate.constant.service;

/**
 * Log message constants for mail template service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MailTemplateServiceLogConstant {

    private MailTemplateServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String MAIL_TEMPLATE_CREATED = "Mail template created: id={}, organizationId={}, createdBy={}";
    public static final String MAIL_TEMPLATE_UPDATED = "Mail template updated: id={}, organizationId={}";
    public static final String MAIL_TEMPLATE_DELETED = "Mail template deleted: id={}, organizationId={}";
    public static final String LISTING_MAIL_TEMPLATES = "Listing mail templates: organizationId={}, request={}";
    public static final String READING_MAIL_TEMPLATE = "Reading mail template: id={}, organizationId={}";
}
