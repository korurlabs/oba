package com.korur.oba.connect.push.constant.controller;

/**
 * Response message constants for push controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PushControllerResponseConstant {

    private PushControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_DEVICE_REGISTERED = "Push device registered successfully";
    public static final String RESPONSE_DEVICE_UNREGISTERED = "Push device unregistered successfully";
    public static final String RESPONSE_PREFERENCES_RETRIEVED = "Push preferences retrieved successfully";
    public static final String RESPONSE_PREFERENCES_UPDATED = "Push preferences updated successfully";
}
