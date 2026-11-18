package com.korur.oba.apps.otak.mailsender.constant.controller;

/**
 * Response message constants for mail sender controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MailSenderControllerResponseConstant {

    private MailSenderControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_MAIL_SENDER_LIST_RETRIEVED = "Mail senders retrieved successfully";
    public static final String RESPONSE_MAIL_SENDER_CREATED = "Mail sender added successfully";
    public static final String RESPONSE_MAIL_SENDER_DELETED = "Mail sender deleted successfully";
}
