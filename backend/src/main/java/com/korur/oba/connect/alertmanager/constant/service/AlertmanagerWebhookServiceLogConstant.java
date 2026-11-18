package com.korur.oba.connect.alertmanager.constant.service;

/**
 * Log messages of the Alertmanager webhook intake service.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AlertmanagerWebhookServiceLogConstant {

    private AlertmanagerWebhookServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String NOTIFICATION_DELEGATED = "Alertmanager notification with {} alerts handed to its handler";
}
