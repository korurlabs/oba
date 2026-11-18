package com.korur.oba.apps.otak.mailsender.constant.controller;

/**
 * Log message constants for mail sender controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MailSenderControllerLogConstant {

    private MailSenderControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String MAIL_SENDER_LIST_REQUEST = "Received request to list mail senders";
    public static final String MAIL_SENDER_LIST_COMPLETED = "List mail senders request completed";

    public static final String MAIL_SENDER_CREATE_REQUEST = "Received request to add mail sender";
    public static final String MAIL_SENDER_CREATE_COMPLETED = "Add mail sender request completed";

    public static final String MAIL_SENDER_DELETE_REQUEST = "Received request to delete mail sender";
    public static final String MAIL_SENDER_DELETE_COMPLETED = "Delete mail sender request completed";
}
