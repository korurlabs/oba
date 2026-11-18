package com.korur.oba.connect.notification.constant.handler;

/**
 * Log message constants for notification outbox handler.
 */
public final class NotificationOutboxHandlerLogConstant {

    private NotificationOutboxHandlerLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String OUTBOX_HANDLER_SENDING_EMAIL = "Sending {} email to: {}";
    public static final String OUTBOX_HANDLER_EMAIL_SENT = "{} email sent to: {}";
}
