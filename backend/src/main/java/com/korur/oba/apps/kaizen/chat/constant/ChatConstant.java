package com.korur.oba.apps.kaizen.chat.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.connect.realtime.constant.RealtimeConstant;

/**
 * Field names, limits, content tokens and parse patterns of the chat domain.
 * The limits are the same on the client, so a message the composer accepts is
 * never refused by the server for its size.
 */
public final class ChatConstant {

    private ChatConstant() {
        // Private constructor to prevent instantiation
    }

    // ── Field names ──────────────────────────────────────────────────────────

    /**
     * Field name constant for message ID.
     */
    public static final String FIELD_MESSAGE_ID = "messageId";

    /**
     * Field name constant for thread ID; also the key of the existing thread in a 409 body.
     */
    public static final String FIELD_THREAD_ID = "threadId";

    /**
     * Field name constant for channel ID.
     */
    public static final String FIELD_CHANNEL_ID = "channelId";

    /**
     * Field name constant for the members of a private channel.
     */
    public static final String FIELD_MEMBER_IDS = "memberIds";

    /**
     * Field name constant for the visibility of a channel.
     */
    public static final String FIELD_VISIBILITY = "visibility";

    /**
     * Field name constant for the purpose of a channel.
     */
    public static final String FIELD_PURPOSE = "purpose";

    /**
     * Field name constant for the name of a channel.
     */
    public static final String FIELD_NAME = "name";

    /**
     * Field name constant for the daily update a message stands for.
     */
    public static final String FIELD_DAILY_UPDATE_ID = "dailyUpdateId";

    /**
     * Field name constant for the pull request share a message stands for.
     */
    public static final String FIELD_PULL_REQUEST_SHARE_ID = "pullRequestShareId";

    /**
     * Field name constant for the root message of a thread.
     */
    public static final String FIELD_ROOT_MESSAGE_ID = "rootMessageId";

    /**
     * Field name constant for the message a reply quotes.
     */
    public static final String FIELD_REPLY_TO_MESSAGE_ID = "replyToMessageId";

    /**
     * Field name constant for organization ID.
     */
    public static final String FIELD_ORGANIZATION_ID = "organizationId";

    /**
     * Field name constant for user ID.
     */
    public static final String FIELD_USER_ID = "userId";

    /**
     * Field name constant for author ID.
     */
    public static final String FIELD_AUTHOR_ID = "authorId";

    /**
     * Field name constant for ticket ID.
     */
    public static final String FIELD_TICKET_ID = "ticketId";

    /**
     * Field name constant for the message text.
     */
    public static final String FIELD_CONTENT = "content";

    /**
     * Field name constant for the files of a send.
     */
    public static final String FIELD_FILES = "files";

    /**
     * Field name constant for the user mentions of a message.
     */
    public static final String FIELD_MENTIONS = "mentions";

    /**
     * Field name constant for the ticket references of a message.
     */
    public static final String FIELD_TICKET_REFS = "ticketRefs";

    /**
     * Field name constant for a reaction's emoji.
     */
    public static final String FIELD_EMOJI = "emoji";

    /**
     * Field name constant for the reactions of a message.
     */
    public static final String FIELD_REACTIONS = "reactions";

    /**
     * Field name constant for the pins of a channel.
     */
    public static final String FIELD_PINS = "pins";

    /**
     * Field name constant for a thread title.
     */
    public static final String FIELD_TITLE = "title";

    /**
     * Field name constant for a thread's auto-archive window.
     */
    public static final String FIELD_AUTO_ARCHIVE_MINUTES = "autoArchiveMinutes";

    /**
     * Field name constant for the paging cursors of a message list.
     */
    public static final String FIELD_CURSOR = "cursor";

    /**
     * Field name constant for the older-than cursor.
     */
    public static final String FIELD_BEFORE = "before";

    /**
     * Field name constant for the newer-than cursor.
     */
    public static final String FIELD_AFTER = "after";

    /**
     * Field name constant for the centred cursor.
     */
    public static final String FIELD_AROUND = "around";

    /**
     * Field name constant for the field a limit applies to.
     */
    public static final String FIELD_LIMIT = "limit";

    /**
     * Entity field name of the creation time, the first key of every message page.
     */
    public static final String FIELD_CREATED_AT = "createdAt";

    // ── Message limits ───────────────────────────────────────────────────────

    /**
     * Longest message text in characters, tokens included, as Discord counts.
     */
    public static final int MAX_CONTENT_LENGTH = 2000;

    /**
     * Most distinct users one message may mention.
     */
    public static final int MAX_USER_MENTIONS = 20;

    /**
     * Most distinct tickets one message may reference.
     */
    public static final int MAX_TICKET_REFS = 10;

    /**
     * Most files one send may attach.
     */
    public static final int MAX_FILES = 10;

    /**
     * Longest client nonce, which the sender uses to replace its optimistic row.
     */
    public static final int MAX_CLIENT_NONCE_LENGTH = 64;

    /**
     * Unique index that allows one message per author and client nonce. A
     * send that loses the race against a concurrent send with the same nonce
     * is recognised by this name and answered with the stored message.
     */
    public static final String CLIENT_NONCE_UNIQUE_INDEX = "chat_messages_author_id_client_nonce_uk";

    /**
     * Unique index that allows one live channel per organization and purpose
     * other than GENERAL. A channel write that loses the race for such a
     * purpose is recognised by this name and refused as a conflict.
     */
    public static final String CHANNEL_PURPOSE_UNIQUE_INDEX = "chat_channels_purpose_uk";

    /**
     * Unique index that allows one live channel per organization and slug. A
     * channel write that loses the race for a slug that was free when it was
     * chosen is recognised by this name and refused as a conflict.
     */
    public static final String CHANNEL_SLUG_UNIQUE_INDEX = "chat_channels_organization_id_slug_uk";

    /**
     * Slug of a channel whose name has no letter or digit left, such as an
     * emoji only.
     */
    public static final String CHANNEL_SLUG_FALLBACK = "channel";

    /**
     * Number of characters of the quoted message shown above a reply.
     */
    public static final int REPLY_SNIPPET_LENGTH = 200;

    /**
     * Suffix of a cut reply snippet: none, the client clamps the snippet to
     * two lines and ends it with its own ellipsis.
     */
    public static final String REPLY_SNIPPET_SUFFIX = "";

    /**
     * Content of a message that has no text: a daily update message, an
     * attachment-only message and the placeholder of a deleted thread root.
     */
    public static final String EMPTY_CONTENT = "";

    // ── Reactions ────────────────────────────────────────────────────────────

    /**
     * Most distinct emoji on one message.
     */
    public static final int MAX_REACTIONS_PER_MESSAGE = 20;

    /**
     * Longest emoji in UTF-16 units; an emoji is also exactly one grapheme.
     */
    public static final int MAX_EMOJI_LENGTH = 64;

    /**
     * Number of user IDs listed per reaction for the "who reacted" tooltip.
     */
    public static final int REACTION_PREVIEW_USERS = 10;

    // ── Pins ─────────────────────────────────────────────────────────────────

    /**
     * Most live pinned messages in one channel, its threads included.
     */
    public static final int MAX_PINS = 50;

    // ── Channels ─────────────────────────────────────────────────────────────

    /**
     * Longest channel name, the length of chat_channels.name.
     */
    public static final int MAX_CHANNEL_NAME_LENGTH = 80;

    /**
     * Most users one request may put on a private channel's member list.
     */
    public static final int MAX_CHANNEL_MEMBERS_PER_REQUEST = 200;

    // ── Paging and search ────────────────────────────────────────────────────

    /**
     * Largest page of messages, pins or threads.
     */
    public static final int MAX_PAGE_SIZE = 50;

    /**
     * Page size when the request names none.
     */
    public static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * Search page size when the request names none.
     */
    public static final int DEFAULT_SEARCH_PAGE_SIZE = 25;

    /**
     * Shortest search text; one character would match almost every message.
     */
    public static final int MIN_SEARCH_LENGTH = 2;

    /**
     * Longest search text.
     */
    public static final int MAX_SEARCH_LENGTH = 100;

    // ── Members ──────────────────────────────────────────────────────────────

    /**
     * Separator between a member's first and last name when the member list
     * matches the search text against the full name.
     */
    public static final String MEMBER_NAME_SEPARATOR = " ";

    /**
     * Text of a missing name, email or search, so a member without a name
     * still sorts and an absent search matches everyone.
     */
    public static final String EMPTY_TEXT = "";

    // ── Unread ───────────────────────────────────────────────────────────────

    /**
     * Highest unread count per scope; the client shows "99+" above 99, so
     * counting further is wasted work.
     */
    public static final int MAX_UNREAD_COUNT = 100;

    // ── Realtime signals ─────────────────────────────────────────────────────

    /**
     * Signal types on the organization topic, the channel topics and the user
     * queue. Clients switch on them, so they are part of the wire contract.
     */
    public static final String SIGNAL_MESSAGE_CREATED = "MESSAGE_CREATED";
    public static final String SIGNAL_MESSAGE_UPDATED = "MESSAGE_UPDATED";
    public static final String SIGNAL_MESSAGE_DELETED = "MESSAGE_DELETED";
    public static final String SIGNAL_THREAD_CREATED = "THREAD_CREATED";
    public static final String SIGNAL_THREAD_UPDATED = "THREAD_UPDATED";
    public static final String SIGNAL_REACTION_CHANGED = "REACTION_CHANGED";
    public static final String SIGNAL_PIN_CHANGED = "PIN_CHANGED";
    public static final String SIGNAL_UNREAD_CHANGED = "UNREAD_CHANGED";
    public static final String SIGNAL_THREAD_MEMBERSHIP_CHANGED = "THREAD_MEMBERSHIP_CHANGED";
    public static final String SIGNAL_TYPING = "TYPING";
    public static final String SIGNAL_PRESENCE = "PRESENCE";
    public static final String SIGNAL_CHANNEL_CHANGED = "CHANNEL_CHANGED";

    /**
     * Destination of typing frames under the application prefix "/app", so a
     * client sends to "/app/chat/typing".
     */
    public static final String TYPING_MESSAGE_MAPPING = "/chat/typing";

    /**
     * Shortest interval between two typing signals of one session, in
     * milliseconds; faster frames are dropped, because each one fans out to
     * every online member. The key stays under the realtime prefix so
     * existing environment settings keep applying.
     */
    public static final String TYPING_MIN_INTERVAL_MS_PLACEHOLDER = "${oba.realtime.typing-min-interval-ms:3000}";

    /**
     * Send bucket of typing frames in the session registry, separate from the
     * general inbound send window, so one accepted typing frame per interval
     * is the whole budget of a session.
     */
    public static final String TYPING_SEND_BUCKET = "chat-typing";

    // ── Realtime destinations ────────────────────────────────────────────────

    /**
     * Name of chat as a realtime consumer, unique among the destination
     * authorizers.
     */
    public static final String CHAT_REALTIME_CONSUMER = "chat";

    /**
     * Suffix of the organization topic every member subscribes to:
     * "/topic/organizations/{organizationId}/chat".
     */
    public static final String CHAT_TOPIC_SUFFIX = "/chat";

    /**
     * Start of the suffix of a channel topic, followed by the channel ID:
     * "/topic/organizations/{organizationId}/chat/channels/{channelId}". It
     * carries the message, pin, reaction, thread and typing signals of that
     * channel, so only its readers receive them.
     */
    public static final String CHAT_CHANNEL_TOPIC_SUFFIX_PREFIX = CHAT_TOPIC_SUFFIX + "/channels/";

    /**
     * Per-user queue a publisher passes to the bus together with the user and
     * organization.
     */
    public static final String CHAT_USER_QUEUE = "/queue/chat";

    /**
     * The per-user queue as the client subscribes to it.
     */
    public static final String CHAT_USER_QUEUE_SUBSCRIPTION = RealtimeConstant.USER_DESTINATION_PREFIX + CHAT_USER_QUEUE;

    /**
     * The one chat destination a client may send to, TYPING_MESSAGE_MAPPING
     * under the application prefix.
     */
    public static final String CHAT_TYPING_DESTINATION =
            RealtimeConstant.APPLICATION_DESTINATION_PREFIX + TYPING_MESSAGE_MAPPING;

    // ── Threads ──────────────────────────────────────────────────────────────

    /**
     * Shortest thread title.
     */
    public static final int MIN_THREAD_TITLE_LENGTH = 1;

    /**
     * Longest thread title.
     */
    public static final int MAX_THREAD_TITLE_LENGTH = 100;

    /**
     * Length at which a default title taken from the root message is cut.
     */
    public static final int DEFAULT_THREAD_TITLE_LENGTH = 40;

    /**
     * Suffix of a default title that was cut.
     */
    public static final String THREAD_TITLE_ELLIPSIS = "…";

    /**
     * Default title when the root message gives no text and no file name.
     */
    public static final String DEFAULT_THREAD_TITLE = "Thread";

    /**
     * Default title of a thread on a daily update: first name, last name and day.
     */
    public static final String DAILY_UPDATE_THREAD_TITLE_FORMAT = "Daily update - %s %s - %s";

    /**
     * Date pattern of the day in DAILY_UPDATE_THREAD_TITLE_FORMAT, for example "20 Oct".
     */
    public static final String DAILY_UPDATE_THREAD_TITLE_DATE_PATTERN = "d MMM";

    /**
     * Locale of the month name in a daily update thread title.
     */
    public static final Locale DAILY_UPDATE_THREAD_TITLE_LOCALE = Locale.ENGLISH;

    /**
     * Auto-archive window when the creator names none: one week.
     */
    public static final int DEFAULT_AUTO_ARCHIVE_MINUTES = 10080;

    /**
     * Allowed auto-archive windows: 1 hour, 24 hours, 3 days and 1 week. The
     * database check chat_threads_auto_archive_minutes_ck holds the same set.
     */
    public static final Set<Integer> AUTO_ARCHIVE_MINUTES_OPTIONS = Set.of(60, 1440, 4320, 10080);

    /**
     * Number of member avatars shown under a thread's parent message.
     */
    public static final int THREAD_MEMBER_PREVIEW_SIZE = 3;

    // ── Content tokens ───────────────────────────────────────────────────────

    /**
     * Token that mentions every member of the organization.
     */
    public static final String TOKEN_EVERYONE = "<@everyone>";

    /**
     * Token that mentions the members online at send time.
     */
    public static final String TOKEN_HERE = "<@here>";

    /**
     * Start of a user mention token, followed by the user ID and TOKEN_SUFFIX.
     */
    public static final String TOKEN_USER_PREFIX = "<@";

    /**
     * Start of a ticket reference token, followed by the ticket ID and TOKEN_SUFFIX.
     */
    public static final String TOKEN_TICKET_PREFIX = "<#";

    /**
     * End of every token.
     */
    public static final String TOKEN_SUFFIX = ">";

    /**
     * Target of the everyone mention inside its token.
     */
    public static final String MENTION_EVERYONE = "everyone";

    /**
     * Target of the here mention inside its token.
     */
    public static final String MENTION_HERE = "here";

    /**
     * Prefix of a mention rendered as plain text, for example "@Ada Lovelace".
     */
    public static final String RENDERED_MENTION_PREFIX = "@";

    /**
     * Plain text of a mention whose user is no longer a member.
     */
    public static final String RENDERED_UNKNOWN_MEMBER = "@unknown member";

    /**
     * Separator between a ticket's key and title in plain text.
     */
    public static final String RENDERED_TICKET_SEPARATOR = " ";

    /**
     * Plain text of a ticket token whose snapshot is not at hand, so no ticket
     * ID or key is shown in its place.
     */
    public static final String RENDERED_UNKNOWN_TICKET = "#unknown ticket";

    // ── Push notifications ───────────────────────────────────────────────────

    /**
     * Prefix of a channel name in a push title, for example "#general".
     */
    public static final String PUSH_CHANNEL_PREFIX = "#";

    /**
     * Separator between a thread's title and its channel in a push title, for
     * example "KZN-311 root cause · #general".
     */
    public static final String PUSH_THREAD_SEPARATOR = " · ";

    /**
     * Separator between the author's first and last name in a push body.
     */
    public static final String PUSH_SENDER_NAME_SEPARATOR = " ";

    /**
     * Sender shown in a push when the author's account has neither a name nor
     * an email.
     */
    public static final String PUSH_UNKNOWN_SENDER = "A member";

    // ── Configuration ────────────────────────────────────────────────────────

    /**
     * Prefix of the send rate properties in application.properties.
     */
    public static final String SEND_RATE_PROPERTIES_PREFIX = "oba.chat.send-rate";

    /**
     * A user mention token. Group 1 is the user ID, "everyone" or "here".
     */
    public static final Pattern USER_TOKEN_PATTERN = Pattern.compile(
            "<@(everyone|here|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})>");

    /**
     * A ticket reference token. Group 1 is the ticket ID.
     */
    public static final Pattern TICKET_TOKEN_PATTERN = Pattern.compile(
            "<#([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})>");

    /**
     * A ticket key typed as plain text: a board prefix of at most 10 characters,
     * a hyphen and a number, as ticket keys are generated.
     */
    public static final Pattern TICKET_KEY_PATTERN = Pattern.compile("\\b[A-Za-z][A-Za-z0-9]{0,9}-\\d{1,9}\\b");

    /**
     * A fenced code block, from an opening fence to the closing fence or the end
     * of the text. It is masked before tokens are read, so nothing inside code
     * is a mention, a reference or a ticket key.
     */
    public static final Pattern FENCED_CODE_PATTERN = Pattern.compile("```[\\s\\S]*?(?:```|\\z)");

    /**
     * An inline code span between single backticks on one line. Masked after
     * the fenced blocks, for the same reason.
     */
    public static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`[^`\\n]+`");

    /**
     * A run of whitespace, line breaks included. A default thread title folds
     * each run into one space, so a multi-line root gives a one-line title.
     */
    public static final Pattern WHITESPACE_RUN_PATTERN = Pattern.compile("\\s+");

    /**
     * Replacement of a whitespace run in a default thread title.
     */
    public static final String TITLE_WHITESPACE = " ";
}
