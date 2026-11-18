package com.korur.oba.connect.push.constant.helper;

/**
 * Log message constants of the store of push tickets that wait for a receipt.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PushTicketTrackerLogConstant {

    private PushTicketTrackerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String TRACKING_LIMIT_REACHED =
            "Push tickets not followed up, the receipt store is full: limit={}, dropped={}";
}
