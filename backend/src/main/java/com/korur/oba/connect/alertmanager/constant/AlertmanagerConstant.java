package com.korur.oba.connect.alertmanager.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Token parsing, payload keys and input limits of the Alertmanager webhook. Its chain's path and
 * authority are in {@link com.korur.oba.dojo.security.constant.SecurityConstant}, with every other
 * chain's.
 */
public final class AlertmanagerConstant {

    private AlertmanagerConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String BEARER_PREFIX = "Bearer ";

    // Refuses startup: a token without its organization would file alerts in no known organization.
    public static final String TOKEN_WITHOUT_ORGANIZATION =
            "oba.integrations.alertmanager.token is set, but oba.integrations.alertmanager.organization-id is not";

    public static final String STATUS_FIRING = "firing";

    public static final String LABEL_ALERTNAME = "alertname";

    public static final String ANNOTATION_SUMMARY = "summary";
    public static final String ANNOTATION_DESCRIPTION = "description";
    public static final String ANNOTATION_RUNBOOK = "runbook";

    // Bounds the work one notification can cause: the receiver sends at most this
    // many alerts (max_alerts).
    public static final int MAX_ALERTS = 50;

    // The fingerprint becomes the ticket's incident key, a 64-character column.
    public static final int FINGERPRINT_MAX_LENGTH = 64;
}
