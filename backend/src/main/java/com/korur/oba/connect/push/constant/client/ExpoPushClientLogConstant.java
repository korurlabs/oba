package com.korur.oba.connect.push.constant.client;

/**
 * Log message constants of the Expo Push Service client. No message carries an Expo push token or a
 * response body, because the bodies Expo returns may name tokens.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ExpoPushClientLogConstant {

    private ExpoPushClientLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String REQUEST_SENT = "Expo request answered: operation={}, items={}";
    public static final String REQUEST_REJECTED = "Expo rejected the request, not retried: operation={}, status={}";
    public static final String REQUEST_FAILED = "Expo request failed, retried: operation={}, status={}";
    public static final String REQUEST_UNREACHABLE = "Expo could not be reached, retried: operation={}, error={}";
    public static final String RESPONSE_UNREADABLE = "Expo answered with a body that cannot be read: operation={}, error={}";
}
