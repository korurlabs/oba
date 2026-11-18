package com.korur.oba.connect.push.constant.exception;

/**
 * Log message constants for refused push requests.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PushExceptionLogConstant {

    private PushExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String DEVICE_NOT_FOUND = "Push device not found for the user: deviceId={}, userId={}";
}
