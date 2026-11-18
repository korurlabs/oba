package com.korur.oba.connect.notification.constant.service;

/**
 * Log message constants used by notification domain services.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class NotificationServiceLogConstant {

    private NotificationServiceLogConstant() {
    }

    public static final String SENDING_EMAIL = "Sending email. template={}, recipients={}";
    public static final String EMAIL_SENT_SUCCESSFULLY = "Email sent successfully. template={}, recipients={}";
    public static final String EMAIL_SEND_FAILED = "Failed to send email after retries. template={}, recipients={}";
    public static final String EMAIL_PERSISTED = "Email notification persisted. template={}, sent={}";
}
