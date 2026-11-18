package com.korur.oba.apps.kazan.chat.constant.controller;

/**
 * Log message constants for the chat controllers: channels, messages, reactions, pins,
 * search, unread counts, threads and typing frames.
 */
public final class ChatControllerLogConstant {

    private ChatControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CHAT_CHANNEL_MESSAGES_LIST_REQUEST = "Received request to list channel messages";
    public static final String CHAT_CHANNEL_MESSAGES_LIST_COMPLETED = "List channel messages request completed";

    public static final String CHAT_MESSAGE_GET_REQUEST = "Received request to get chat message";
    public static final String CHAT_MESSAGE_GET_COMPLETED = "Get chat message request completed";

    public static final String CHAT_MESSAGE_CREATE_REQUEST = "Received request to send chat message";
    public static final String CHAT_MESSAGE_CREATE_COMPLETED = "Send chat message request completed";

    public static final String CHAT_MESSAGE_EDIT_REQUEST = "Received request to edit chat message";
    public static final String CHAT_MESSAGE_EDIT_COMPLETED = "Edit chat message request completed";

    public static final String CHAT_MESSAGE_DELETE_REQUEST = "Received request to delete chat message";
    public static final String CHAT_MESSAGE_DELETE_COMPLETED = "Delete chat message request completed";

    public static final String CHAT_REACTION_ADD_REQUEST = "Received request to add chat reaction";
    public static final String CHAT_REACTION_ADD_COMPLETED = "Add chat reaction request completed";

    public static final String CHAT_REACTION_REMOVE_REQUEST = "Received request to remove chat reaction";
    public static final String CHAT_REACTION_REMOVE_COMPLETED = "Remove chat reaction request completed";

    public static final String CHAT_MESSAGE_PIN_REQUEST = "Received request to pin chat message";
    public static final String CHAT_MESSAGE_PIN_COMPLETED = "Pin chat message request completed";

    public static final String CHAT_MESSAGE_UNPIN_REQUEST = "Received request to unpin chat message";
    public static final String CHAT_MESSAGE_UNPIN_COMPLETED = "Unpin chat message request completed";

    public static final String CHAT_PINS_LIST_REQUEST = "Received request to list pinned chat messages";
    public static final String CHAT_PINS_LIST_COMPLETED = "List pinned chat messages request completed";

    public static final String CHAT_SEARCH_REQUEST = "Received request to search chat messages";
    public static final String CHAT_SEARCH_COMPLETED = "Search chat messages request completed";

    public static final String CHAT_MEMBERS_LIST_REQUEST = "Received request to list chat members";
    public static final String CHAT_MEMBERS_LIST_COMPLETED = "List chat members request completed";

    public static final String CHAT_UNREAD_GET_REQUEST = "Received request to get chat unread counts";
    public static final String CHAT_UNREAD_GET_COMPLETED = "Get chat unread counts request completed";

    public static final String CHAT_READ_STATE_UPDATE_REQUEST = "Received request to mark chat read";
    public static final String CHAT_READ_STATE_UPDATE_COMPLETED = "Mark chat read request completed";

    public static final String CHAT_THREAD_CREATE_REQUEST = "Received request to create chat thread";
    public static final String CHAT_THREAD_CREATE_COMPLETED = "Create chat thread request completed";

    public static final String CHAT_THREADS_LIST_REQUEST = "Received request to list chat threads";
    public static final String CHAT_THREADS_LIST_COMPLETED = "List chat threads request completed";

    public static final String CHAT_THREAD_GET_REQUEST = "Received request to get chat thread";
    public static final String CHAT_THREAD_GET_COMPLETED = "Get chat thread request completed";

    public static final String CHAT_THREAD_UPDATE_REQUEST = "Received request to update chat thread";
    public static final String CHAT_THREAD_UPDATE_COMPLETED = "Update chat thread request completed";

    public static final String CHAT_THREAD_STATUS_UPDATE_REQUEST = "Received request to change chat thread status";
    public static final String CHAT_THREAD_STATUS_UPDATE_COMPLETED = "Change chat thread status request completed";

    public static final String CHAT_THREAD_MESSAGES_LIST_REQUEST = "Received request to list chat thread messages";
    public static final String CHAT_THREAD_MESSAGES_LIST_COMPLETED = "List chat thread messages request completed";

    public static final String CHAT_THREAD_JOIN_REQUEST = "Received request to join chat thread";
    public static final String CHAT_THREAD_JOIN_COMPLETED = "Join chat thread request completed";

    public static final String CHAT_THREAD_LEAVE_REQUEST = "Received request to leave chat thread";
    public static final String CHAT_THREAD_LEAVE_COMPLETED = "Leave chat thread request completed";

    public static final String CHAT_TYPING_THROTTLED = "Chat typing frame dropped: sent within the minimum interval. sessionId={}";
    public static final String CHAT_TYPING_REFUSED = "Chat typing frame dropped: refused by the access, channel or thread check. userId={}, organizationId={}, errorCode={}, errorValues={}";
    public static final String CHAT_TYPING_BODY_UNREADABLE = "Chat typing frame dropped: unreadable body. userId={}, organizationId={}, reason={}";

    public static final String CHAT_CHANNELS_LIST_REQUEST = "Received request to list chat channels";
    public static final String CHAT_CHANNELS_LIST_COMPLETED = "List chat channels request completed";
    public static final String CHAT_CHANNEL_CREATE_REQUEST = "Received request to create chat channel";
    public static final String CHAT_CHANNEL_CREATE_COMPLETED = "Create chat channel request completed";
    public static final String CHAT_CHANNEL_UPDATE_REQUEST = "Received request to update chat channel";
    public static final String CHAT_CHANNEL_UPDATE_COMPLETED = "Update chat channel request completed";
    public static final String CHAT_CHANNEL_DELETE_REQUEST = "Received request to delete chat channel";
    public static final String CHAT_CHANNEL_DELETE_COMPLETED = "Delete chat channel request completed";
    public static final String CHAT_CHANNEL_MEMBERS_ADD_REQUEST = "Received request to add chat channel members";
    public static final String CHAT_CHANNEL_MEMBERS_ADD_COMPLETED = "Add chat channel members request completed";
    public static final String CHAT_CHANNEL_MEMBER_REMOVE_REQUEST = "Received request to remove chat channel member";
    public static final String CHAT_CHANNEL_MEMBER_REMOVE_COMPLETED = "Remove chat channel member request completed";
}
