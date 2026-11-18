package com.korur.oba.connect.push.constant.service;

/**
 * Log message constants of push dispatch. No message carries an Expo push token or any push copy,
 * because the copy holds message text.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PushDispatchServiceLogConstant {

    private PushDispatchServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String DISPATCH_SKIPPED_DISABLED = "Push not sent, sending is off: organizationId={}, recipients={}";
    public static final String DISPATCH_COMPLETED =
            "Push dispatched: organizationId={}, recipients={}, eligibleRecipients={}, devices={}, accepted={}, failed={}";
    public static final String CHUNK_FAILED = "Push chunk not delivered to Expo: organizationId={}, messages={}";
    public static final String TICKET_COUNT_MISMATCH = "Expo returned another number of tickets than messages: messages={}, tickets={}";
    // Expo's error message is not logged: for some errors it quotes the token.
    public static final String TICKET_ERROR = "Expo refused a push: organizationId={}, userId={}, error={}";
    public static final String TOKEN_REMOVAL_FAILED = "Removing push devices Expo no longer accepts failed: tokens={}";
}
