package com.korur.oba.connect.alertmanager.constant.controller;

/**
 * Log message constants for the Alertmanager webhook controller.
 */
public final class AlertmanagerControllerLogConstant {

    private AlertmanagerControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String ALERTMANAGER_WEBHOOK_REQUEST = "Received Alertmanager notification with {} alerts";
    public static final String ALERTMANAGER_WEBHOOK_COMPLETED = "Alertmanager notification completed: created {}, ignored {}";
}
