package com.korur.oba.apps.kaizen.chat.constant.listener;

/**
 * Log message constants of the chat event listeners. No message carries the message text or a name.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ChatListenerLogConstant {

    private ChatListenerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CHAT_PUSH_RECIPIENTS_WITHOUT_KAIZEN_SKIPPED = "Chat push skipped recipients without Kaizen access: organizationId={}, messageId={}, skipped={}, remaining={}";
}
