package com.korur.oba.connect.push.constant.service;

/**
 * Log message constants for push device service operations. No message carries an Expo push token,
 * because anyone who holds one can push to the device.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PushDeviceServiceLogConstant {

    private PushDeviceServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String DEVICE_REGISTERED = "Push device registered: deviceId={}, userId={}, platform={}";
    public static final String DEVICE_MOVED = "Push device moved to another user: deviceId={}, previousUserId={}, userId={}";
    public static final String DEVICE_UNREGISTERED = "Push device unregistered: deviceId={}, userId={}";
    public static final String TOKENS_REMOVED = "Push devices removed for tokens Expo no longer accepts: requested={}, removed={}";
}
