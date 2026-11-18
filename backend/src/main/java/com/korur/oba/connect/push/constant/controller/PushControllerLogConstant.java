package com.korur.oba.connect.push.constant.controller;

/**
 * Log message constants for push controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PushControllerLogConstant {

    private PushControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String DEVICE_REGISTER_REQUEST = "Received request to register push device: platform={}";
    public static final String DEVICE_REGISTER_COMPLETED = "Register push device request completed: deviceId={}";

    public static final String DEVICE_UNREGISTER_REQUEST = "Received request to unregister push device: deviceId={}";
    public static final String DEVICE_UNREGISTER_COMPLETED = "Unregister push device request completed: deviceId={}";

    public static final String PREFERENCES_GET_REQUEST = "Received request to get push preferences";
    public static final String PREFERENCES_GET_COMPLETED = "Get push preferences request completed";

    public static final String PREFERENCES_UPDATE_REQUEST = "Received request to update push preferences";
    public static final String PREFERENCES_UPDATE_COMPLETED = "Update push preferences request completed";
}
