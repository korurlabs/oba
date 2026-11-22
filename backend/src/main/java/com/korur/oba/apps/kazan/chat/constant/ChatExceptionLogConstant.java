package com.korur.oba.apps.kazan.chat.constant;

/**
 * Log message constants for chat domain exceptions.
 */
public final class ChatExceptionLogConstant {

    private ChatExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for when a message is absent, deleted or of another organization.
     */
    public static final String CHAT_MESSAGE_NOT_FOUND = "Chat message not found: messageId={}, organizationId={}";

    /**
     * Log message for when a thread is absent or of another organization.
     */
    public static final String CHAT_THREAD_NOT_FOUND = "Chat thread not found: threadId={}, organizationId={}";

    /**
     * Log message for when a message has neither text nor files.
     */
    public static final String CHAT_MESSAGE_EMPTY = "Chat message has no text and no attachment: userId={}";

    /**
     * Log message for when a message breaks a size or count limit.
     */
    public static final String CHAT_MESSAGE_LIMIT_EXCEEDED = "Chat message exceeds a limit: field={}, limit={}, actual={}";

    /**
     * Log message for when a page of messages, pins or threads asks for a size outside the allowed range.
     */
    public static final String CHAT_PAGE_SIZE_OUT_OF_RANGE = "Chat page size out of range: limit={}, min={}, max={}";

    /**
     * Log message for when a message mentions a user who is not an active member.
     */
    public static final String CHAT_MENTION_NOT_MEMBER = "Chat message mentions a non-member: userId={}, organizationId={}";

    /**
     * Log message for when a referenced ticket is missing or not on the author's boards.
     */
    public static final String CHAT_TICKET_REFS_NOT_ACCESSIBLE = "Chat ticket references not found or not accessible: {}";

    /**
     * Log message for when a reply targets a message of another channel or thread.
     */
    public static final String CHAT_REPLY_OUTSIDE_CONVERSATION = "Chat reply target is outside the conversation: replyToMessageId={}, threadId={}";

    /**
     * Log message for when a user edits another user's message.
     */
    public static final String CHAT_MESSAGE_NOT_AUTHOR = "User is not the author of chat message: messageId={}, userId={}";

    /**
     * Log message for when a user who is neither author nor owner or admin deletes a message.
     */
    public static final String CHAT_DELETE_NOT_ALLOWED = "User may not delete chat message: messageId={}, userId={}, role={}";

    /**
     * Log message for when a daily update message is edited or deleted through the chat endpoints.
     */
    public static final String CHAT_DAILY_UPDATE_MESSAGE_READ_ONLY = "Chat message is a daily update and read-only here: messageId={}";

    /**
     * Log message for when an emoji is not exactly one grapheme or is too long.
     */
    public static final String CHAT_REACTION_INVALID_EMOJI = "Chat reaction emoji is invalid: messageId={}, length={}";

    /**
     * Log message for when a message already holds the most distinct emoji.
     */
    public static final String CHAT_REACTION_LIMIT_REACHED = "Chat message has reached the reaction limit: messageId={}, limit={}";

    /**
     * Log message for when a concurrent insert of the same reaction hit the unique constraint.
     */
    public static final String CHAT_REACTION_ALREADY_EXISTS = "Chat reaction already exists, read as reacted: messageId={}, userId={}, emoji={}";

    /**
     * Log message for when a user who is neither author nor owner or admin pins a message.
     */
    public static final String CHAT_PIN_NOT_ALLOWED = "User may not pin or unpin chat message: messageId={}, userId={}";

    /**
     * Log message for when the channel already holds the most pins.
     */
    public static final String CHAT_PIN_LIMIT_REACHED = "Chat channel has reached the pin limit: channelId={}, limit={}";

    /**
     * Log message for when the cursor of a pin page is no longer pinned, so it has no place in the pin list.
     */
    public static final String CHAT_PIN_CURSOR_NOT_PINNED = "Chat pin page cursor is not pinned: messageId={}, organizationId={}";

    /**
     * Log message for when a page request names more than one cursor.
     */
    public static final String CHAT_CURSOR_CONFLICT = "Chat page request names more than one cursor: before={}, after={}, around={}";

    /**
     * Log message for when a thread is started on a thread message or a deleted message.
     */
    public static final String CHAT_THREAD_ROOT_INVALID = "Chat thread root is not a live channel message: rootMessageId={}";

    /**
     * Log message for when an auto-archive window is not one of the allowed options.
     */
    public static final String CHAT_THREAD_AUTO_ARCHIVE_INVALID = "Chat thread auto-archive window is invalid: threadId={}, minutes={}";

    /**
     * Log message for when a second thread is started on the same message.
     */
    public static final String CHAT_THREAD_EXISTS = "Chat message already has a thread: rootMessageId={}, threadId={}";

    /**
     * Log message for when a user who is neither creator nor owner or admin changes a thread.
     */
    public static final String CHAT_THREAD_MANAGE_NOT_ALLOWED = "User may not change chat thread: threadId={}, userId={}";

    /**
     * Log message for when a reply or an edit is refused because its thread is closed.
     */
    public static final String CHAT_THREAD_CLOSED = "Chat thread is closed: threadId={}";

    /**
     * Log message for when a closed thread is to be archived without being reopened first.
     */
    public static final String CHAT_THREAD_CLOSED_NOT_ARCHIVABLE = "Closed chat thread cannot be archived: threadId={}";

    /**
     * Log message for when a user sends faster than the send rate allows.
     */
    public static final String CHAT_SEND_RATE_EXCEEDED = "Chat send rate exceeded: userId={}";

    /**
     * Log message for when a signal could not be published after commit.
     */
    public static final String CHAT_SIGNAL_PUBLISH_FAILED = "Chat signal publish failed: type={}, organizationId={}, id={}";

    /**
     * Log message for when the reader-neutral copy of a new or edited message, or its version, could not be read for its signal.
     */
    public static final String CHAT_SIGNAL_PAYLOAD_FAILED = "Chat signal payload build failed: type={}, organizationId={}, messageId={}";

    /**
     * Log message for when a signal to one user's queue could not be published after commit.
     */
    public static final String CHAT_USER_SIGNAL_PUBLISH_FAILED = "Chat user signal publish failed: type={}, userId={}, organizationId={}, id={}";

    /**
     * Log message for when the unread counts of a fan-out could not be read after commit.
     */
    public static final String CHAT_UNREAD_FANOUT_FAILED = "Chat unread fan-out failed: organizationId={}, threadId={}, id={}";

    /**
     * Log message for when the badge counts of a chat push could not be read; the push goes without badges.
     */
    public static final String CHAT_PUSH_BADGES_FAILED = "Chat push badge counts failed, sending without badges: organizationId={}, messageId={}";

    /**
     * Log message for when the push of a new chat message could not be dispatched after commit.
     */
    public static final String CHAT_PUSH_DISPATCH_FAILED = "Chat push dispatch failed: organizationId={}, channelId={}, threadId={}, messageId={}";

    /**
     * Log message for when the read push that withdraws a conversation's notifications could not be dispatched.
     */
    public static final String CHAT_READ_PUSH_DISPATCH_FAILED = "Chat read push dispatch failed: organizationId={}, userId={}, channelId={}, threadId={}";

    /**
     * Log message for when a channel is absent, deleted, of another organization or private without the user on its member list.
     */
    public static final String CHAT_CHANNEL_NOT_FOUND = "Chat channel not found or not readable: channelId={}, organizationId={}, userId={}";

    /**
     * Log message for when members are given outside a switch from public to private.
     */
    public static final String CHAT_CHANNEL_MEMBERS_NOT_EXPECTED = "Chat channel members given outside a switch to private: channelId={}, visibility={}";

    /**
     * Log message for when members are managed on a public channel.
     */
    public static final String CHAT_CHANNEL_NOT_PRIVATE = "Chat channel members managed on a public channel: channelId={}";

    /**
     * Log message for when a purpose other than GENERAL meets a private channel.
     */
    public static final String CHAT_CHANNEL_PURPOSE_PRIVATE = "Chat channel with purpose {} cannot be private: channelId={}";

    /**
     * Log message for when a channel asks for a purpose that another live channel holds.
     */
    public static final String CHAT_CHANNEL_PURPOSE_TAKEN = "Chat channel purpose {} is held by another channel: channelId={}, holderId={}, organizationId={}";

    /**
     * Log message for when the unique purpose index refuses a channel write that lost a race for a purpose.
     */
    public static final String CHAT_CHANNEL_PURPOSE_RACE_LOST = "Chat channel purpose {} taken by a concurrent write: channelId={}, organizationId={}, cause={}";

    /**
     * Log message for when the unique slug index refuses a channel write that lost a race for a slug.
     */
    public static final String CHAT_CHANNEL_SLUG_RACE_LOST = "Chat channel slug {} taken by a concurrent write: channelId={}, organizationId={}, cause={}";

    /**
     * Log message for when a user put on a channel's member list is no active member of the organization.
     */
    public static final String CHAT_CHANNEL_MEMBER_NOT_ACTIVE = "Chat channel member is no active member of the organization: userId={}, organizationId={}";

    /**
     * Log message for when a user below ADMIN removes another user from a channel's member list.
     */
    public static final String CHAT_CHANNEL_MEMBER_REMOVE_NOT_ALLOWED = "User may not remove another chat channel member: channelId={}, memberId={}, userId={}, role={}";

    /**
     * Log message for when a thread named with a channel belongs to another channel.
     */
    public static final String CHAT_THREAD_OUTSIDE_CHANNEL = "Chat thread is not in the channel: threadId={}, channelId={}";

    /**
     * Log message for when a daily update entry is posted outside the daily updates channel.
     */
    public static final String CHAT_DAILY_UPDATE_CHANNEL_INVALID = "Chat daily update posted outside the daily updates channel: dailyUpdateId={}, channelId={}, organizationId={}";

    /**
     * Log message for when a user posts the entry of another user's daily update.
     */
    public static final String CHAT_DAILY_UPDATE_NOT_AUTHOR = "User is not the author of the daily update: dailyUpdateId={}, userId={}";

    /**
     * Log message for when a daily update already has its chat entry.
     */
    public static final String CHAT_DAILY_UPDATE_ALREADY_POSTED = "Chat daily update already has an entry: dailyUpdateId={}";

    /**
     * Log message for when a daily update entry names an update that is absent, deleted or of another organization.
     */
    public static final String CHAT_DAILY_UPDATE_NOT_FOUND = "Chat daily update not found in the organization: dailyUpdateId={}, organizationId={}";

    /**
     * Log message for when a daily update entry is sent into a thread, where it cannot stand.
     */
    public static final String CHAT_DAILY_UPDATE_IN_THREAD = "Chat daily update entry sent into a thread: dailyUpdateId={}, threadId={}";

    /**
     * Log message for when a channel signal could not be sent after commit.
     */
    public static final String CHAT_CHANNEL_SIGNAL_PUBLISH_FAILED = "Chat channel signal publish failed: type={}, organizationId={}, channelId={}";

    /**
     * Log message for when the subscriptions of users who may have lost a channel could not be checked again.
     */
    public static final String CHAT_CHANNEL_REVALIDATION_FAILED = "Chat channel subscription revalidation failed: organizationId={}, channelId={}, users={}";

    /**
     * Log message for when a pull request share entry names a share that is absent, deleted or of another organization.
     */
    public static final String CHAT_PULL_REQUEST_SHARE_NOT_FOUND = "Chat pull request share not found in the organization: pullRequestShareId={}, organizationId={}";

    /**
     * Log message for when a user posts the entry of another user's pull request share.
     */
    public static final String CHAT_PULL_REQUEST_SHARE_NOT_AUTHOR = "User is not the author of the pull request share: pullRequestShareId={}, userId={}";

    /**
     * Log message for when a pull request share entry is posted outside the pull requests channel.
     */
    public static final String CHAT_PULL_REQUEST_SHARE_CHANNEL_INVALID = "Chat pull request share posted outside the pull requests channel: pullRequestShareId={}, channelId={}, organizationId={}";

    /**
     * Log message for when a pull request share already has its chat entry.
     */
    public static final String CHAT_PULL_REQUEST_SHARE_ALREADY_POSTED = "Chat pull request share already has an entry: pullRequestShareId={}";

    /**
     * Log message for when a pull request share entry is sent into a thread, with files or with a daily update.
     */
    public static final String CHAT_PULL_REQUEST_SHARE_NOT_ALONE = "Chat pull request share entry sent with a {}: pullRequestShareId={}";

    /**
     * Log message for when a chat edit or delete targets a pull request share entry.
     */
    public static final String CHAT_PULL_REQUEST_SHARE_MESSAGE_READ_ONLY = "Chat message is a pull request share entry and read-only here: messageId={}, pullRequestShareId={}";
}
