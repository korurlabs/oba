package com.korur.oba.apps.kazan.chat.constant.listener;

/**
 * Log message constants of the chat event listeners. No message carries the message text or a name.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ChatListenerLogConstant {

    private ChatListenerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CHAT_PUSH_RECIPIENTS_WITHOUT_KAZAN_SKIPPED = "Chat push skipped recipients without Kazan access: organizationId={}, messageId={}, skipped={}, remaining={}";
}
