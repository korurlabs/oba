package com.korur.oba.apps.kazan.chat.constant.service;

/**
 * Log message constants for chat service operations.
 */
public final class ChatServiceLogConstant {

    private ChatServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CHAT_MESSAGE_CREATED = "Chat message created: {}. Organization: {}, thread: {}, mentions: {}, ticket refs: {}, files: {}";
    public static final String CHAT_MESSAGE_SEND_REPEATED = "Chat message send repeated: {} answered for client nonce {}, deleted: {}";
    public static final String CHAT_MESSAGE_SEND_RACE_LOST = "Chat message send lost the race for client nonce {} of author {}; answering with the stored message";
    public static final String CHAT_MESSAGE_EDITED = "Chat message edited: {}. Mentions: {}, ticket refs: {}";
    public static final String CHAT_MESSAGE_DELETED = "Chat message deleted: {}";
    public static final String CHAT_MESSAGE_PINNED = "Chat message pinned: {} by {}";
    public static final String CHAT_MESSAGE_UNPINNED = "Chat message unpinned: {} by {}";
    public static final String CHAT_MESSAGE_PIN_UNCHANGED = "Chat message pin unchanged, already in the requested state: {}, pinned: {}, by {}";
    public static final String CHAT_DAILY_UPDATE_MESSAGE_POSTED = "Chat daily update message posted: {} for daily update {} in organization {}";
    public static final String CHAT_DAILY_UPDATE_MESSAGE_TOUCHED = "Chat daily update message touched: {} for daily update {}";
    public static final String CHAT_DAILY_UPDATE_MESSAGE_REMOVED = "Chat daily update message removed: {} for daily update {}";
    public static final String CHAT_DAILY_UPDATE_MESSAGE_ABSENT = "Chat daily update message absent for daily update {}; nothing to change";
    public static final String CHAT_PULL_REQUEST_SHARE_MESSAGE_POSTED = "Chat pull request share message posted: {} for share {} in organization {}";
    public static final String CHAT_PULL_REQUEST_SHARE_MESSAGE_TOUCHED = "Chat pull request share message touched: {} for share {}, edited: {}";
    public static final String CHAT_PULL_REQUEST_SHARE_MESSAGE_REMOVED = "Chat pull request share message removed: {} for share {}";
    public static final String CHAT_PULL_REQUEST_SHARE_MESSAGE_ABSENT = "Chat pull request share message absent for share {}; nothing to change";
    public static final String CHAT_REACTION_ADDED = "Chat reaction added: message {}, user {}, emoji {}";
    public static final String CHAT_REACTION_REMOVED = "Chat reaction removed: message {}, user {}, emoji {}";
    public static final String CHAT_THREAD_CREATED = "Chat thread created: {} on message {}";
    public static final String CHAT_THREAD_UPDATED = "Chat thread updated: {}. Auto-archive minutes: {}";
    public static final String CHAT_THREAD_STATUS_CHANGED = "Chat thread status changed: {} from {} to {} by {}";
    public static final String CHAT_THREAD_STATUS_UNCHANGED = "Chat thread status already {}: {}";
    public static final String CHAT_THREAD_REVIVED = "Chat thread {} made active by a reply from {}";
    public static final String CHAT_THREAD_JOINED = "Chat thread joined: {} by {}";
    public static final String CHAT_THREAD_LEFT = "Chat thread left: {} by {}";
    public static final String CHAT_THREAD_MEMBERS_ADDED = "Chat thread {} gained members: {}";
    public static final String CHAT_PUSH_REQUESTED = "Chat message {} requests pushes for {} users";
    public static final String CHAT_NOTIFICATIONS_CHANGED ="Chat message {} changed notifications: users {}, everyone toggled: {}";
    public static final String CHAT_READ_STATE_ADVANCED = "Chat read state advanced: user {}, organization {}, thread {}, message {}";
    public static final String CHAT_READ_STATE_SKIPPED_NOT_MEMBER = "Chat read state not stored, user is no member of the thread: user {}, organization {}, thread {}";
    public static final String CHAT_TYPING_PUBLISHED = "Chat typing signal published: user {}, organization {}, channel {}, thread {}";

    public static final String CHAT_CHANNEL_CREATED = "Chat channel created: {} in organization {}. Visibility: {}, purpose: {}, members: {}";
    public static final String CHAT_CHANNEL_UPDATED = "Chat channel updated: {}. Visibility: {}, purpose: {}";
    public static final String CHAT_CHANNEL_VISIBILITY_CHANGED = "Chat channel visibility changed: {} from {} to {}";
    public static final String CHAT_CHANNEL_DELETED = "Chat channel deleted: {} by {}";
    public static final String CHAT_CHANNEL_MEMBERS_ADDED = "Chat channel {} gained members: {}";
    public static final String CHAT_CHANNEL_MEMBER_REMOVED = "Chat channel {} lost member {}, removed by {}";
    public static final String CHAT_CHANNEL_MEMBER_REMOVE_UNCHANGED = "Chat channel member not on the list, nothing removed: channel {}, user {}";
    public static final String CHAT_CHANNELS_LISTED = "Chat channels listed for user {} in organization {}: {}";
    public static final String CHAT_INITIAL_CHANNELS_CREATED = "Chat initial channels created for organization {}: {}";
    public static final String CHAT_DAILY_UPDATE_CHANNEL_ABSENT = "Chat daily update card skipped: organization {} has no daily updates channel, daily update {}";
}
