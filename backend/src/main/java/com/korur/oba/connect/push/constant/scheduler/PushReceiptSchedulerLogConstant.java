package com.korur.oba.connect.push.constant.scheduler;

/**
 * Log message constants of the push receipt check. No message carries an Expo push token.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PushReceiptSchedulerLogConstant {

    private PushReceiptSchedulerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RECEIPTS_CHECKED = "Push receipts checked: due={}, received={}, deviceNotRegistered={}, expired={}";
    public static final String RECEIPT_CHUNK_FAILED = "Push receipts not read from Expo, the rest wait for the next run: tickets={}";
    // Expo's error message is not logged: for some errors it quotes the token.
    public static final String RECEIPT_ERROR = "Expo could not deliver a push: ticketId={}, error={}";
    public static final String TOKEN_REMOVAL_FAILED = "Removing push devices Expo no longer accepts failed: tokens={}";
}
