package com.korur.oba.connect.alertmanager.constant.security;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the Alertmanager webhook authentication. One line per call
 * names the method, the URI, the remote address and the outcome; the bearer
 * token is never part of it.
 */
public final class AlertmanagerSecurityLogConstant {

    private AlertmanagerSecurityLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String WEBHOOK_AUTHENTICATION = "Alertmanager webhook {} {} from {}: {}";

    public static final String OUTCOME_AUTHENTICATED = "AUTHENTICATED";
    public static final String OUTCOME_REJECTED = "REJECTED";
}
