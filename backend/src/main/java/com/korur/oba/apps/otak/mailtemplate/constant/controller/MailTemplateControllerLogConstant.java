package com.korur.oba.apps.otak.mailtemplate.constant.controller;

/**
 * Log message constants for mail template controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MailTemplateControllerLogConstant {

    private MailTemplateControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String MAIL_TEMPLATE_LIST_REQUEST = "Received request to list mail templates";
    public static final String MAIL_TEMPLATE_LIST_COMPLETED = "List mail templates request completed";

    public static final String MAIL_TEMPLATE_CREATE_REQUEST = "Received request to create mail template";
    public static final String MAIL_TEMPLATE_CREATE_COMPLETED = "Create mail template request completed";

    public static final String MAIL_TEMPLATE_GET_REQUEST = "Received request to get mail template";
    public static final String MAIL_TEMPLATE_GET_COMPLETED = "Get mail template request completed";

    public static final String MAIL_TEMPLATE_UPDATE_REQUEST = "Received request to update mail template";
    public static final String MAIL_TEMPLATE_UPDATE_COMPLETED = "Update mail template request completed";

    public static final String MAIL_TEMPLATE_DELETE_REQUEST = "Received request to delete mail template";
    public static final String MAIL_TEMPLATE_DELETE_COMPLETED = "Delete mail template request completed";
}
