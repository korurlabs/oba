package com.korur.oba.connect.notification.constant;

/**
 * Shared constants for the notification domain.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class NotificationConstant {

    private NotificationConstant() {
    }

    // Branding
    public static final String EMAIL_SUBJECT_PREFIX = "Sensey - ";
    public static final String SENSEY_LOGO_VARIABLE = "senseyLogoBase64";
    public static final String COMMON_CSS_VARIABLE = "commonCSS";
    public static final String TIMESTAMP_VARIABLE = "timestamp";

    /**
     * Pattern of the email timestamp. No user zone is stored, so the time is
     * written in UTC and labelled, for a reader outside the app.
     */
    public static final String TIMESTAMP_PATTERN = "d MMMM yyyy 'at' HH:mm 'UTC'";

    // Resource paths
    public static final String CSS_CLASSPATH = "classpath:static/css/email-common.css";
    public static final String LOGO_CLASSPATH = "static/images/korur-logo.png";
}
