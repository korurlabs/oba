package com.korur.oba.connect.push.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Duration;

// 4. Internal

/**
 * Request paths, validation rules, field names, Expo Push Service settings and push copy of the push
 * notification domain.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PushConstant {

    private PushConstant() {
        // Private constructor to prevent instantiation
    }

    // --- Paths ---
    // No organization in the path: devices and preferences belong to the user and serve every
    // organization the user is a member of.
    public static final String PUSH_PATH = "/push";
    public static final String DEVICES_PATH = "/devices";
    public static final String DEVICE_PATH = DEVICES_PATH + "/{deviceId}";
    public static final String PREFERENCES_PATH = "/preferences";

    // --- Validation ---
    // The form Expo issues tokens in; the database checks the same pattern.
    public static final String EXPO_PUSH_TOKEN_REGEX = "^ExponentPushToken\\[.+\\]$";
    public static final int EXPO_PUSH_TOKEN_MAX_LENGTH = 255;
    // The languages the push copy is written in; the database checks the same list.
    public static final String LOCALE_REGEX = "^(en|nl|tr)$";
    public static final int DEVICE_NAME_MAX_LENGTH = 100;
    public static final int APP_VERSION_MAX_LENGTH = 32;

    // --- Validation messages ---
    public static final String MSG_EXPO_PUSH_TOKEN_REQUIRED = "Expo push token is required";
    public static final String MSG_EXPO_PUSH_TOKEN_SIZE = "Expo push token must not exceed 255 characters";
    public static final String MSG_EXPO_PUSH_TOKEN_FORMAT = "Expo push token must have the form ExponentPushToken[...]";
    public static final String MSG_PLATFORM_REQUIRED = "Platform is required";
    public static final String MSG_DEVICE_NAME_SIZE = "Device name must not exceed 100 characters";
    public static final String MSG_APP_VERSION_REQUIRED = "App version is required";
    public static final String MSG_APP_VERSION_SIZE = "App version must not exceed 32 characters";
    public static final String MSG_LOCALE_REQUIRED = "Locale is required";
    public static final String MSG_LOCALE_FORMAT = "Locale must be one of en, nl, tr";
    public static final String MSG_MENTIONS_AND_REPLIES_REQUIRED = "mentionsAndReplies is required";
    public static final String MSG_EVERYONE_REQUIRED = "everyone is required";
    public static final String MSG_HERE_REQUIRED = "here is required";
    public static final String MSG_URGENT_INCIDENTS_REQUIRED = "urgentIncidents is required";
    public static final String MSG_SHOW_PREVIEWS_REQUIRED = "showPreviews is required";

    // --- Field names ---
    public static final String FIELD_DEVICE_ID = "deviceId";
    public static final String FIELD_OPERATION = "operation";
    public static final String FIELD_STATUS = "status";

    // --- Expo Push Service ---
    public static final String EXPO_PROPERTIES_PREFIX = "oba.integrations.expo";
    public static final String EXPO_API_BASE_URL = "https://exp.host";
    public static final String EXPO_PATH_SEND = "/--/api/v2/push/send";
    public static final String EXPO_PATH_RECEIPTS = "/--/api/v2/push/getReceipts";
    // The one key of a receipt request body: the ticket ids to read.
    public static final String EXPO_FIELD_IDS = "ids";
    public static final Duration EXPO_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration EXPO_READ_TIMEOUT = Duration.ofSeconds(15);
    // Expo accepts at most 100 messages per send request and 1000 ticket ids per receipt request.
    public static final int EXPO_SEND_CHUNK_SIZE = 100;
    public static final int EXPO_RECEIPT_CHUNK_SIZE = 1000;
    public static final int HTTP_STATUS_TOO_MANY_REQUESTS = 429;
    public static final String EXPO_STATUS_OK = "ok";
    public static final String EXPO_STATUS_ERROR = "error";
    // The ticket or receipt error after which Expo will never deliver to the token again.
    public static final String EXPO_ERROR_DEVICE_NOT_REGISTERED = "DeviceNotRegistered";
    public static final String OPERATION_SEND = "send";
    public static final String OPERATION_GET_RECEIPTS = "getReceipts";

    // --- Expo message fields ---
    public static final String SOUND_DEFAULT = "default";
    public static final String PRIORITY_HIGH = "high";
    // Apple refuses priority 10 for a push that only wakes the app, and Expo sends "normal" as 5.
    public static final String PRIORITY_NORMAL = "normal";
    // Android notification channels; the app creates the same ids at startup.
    public static final String ANDROID_CHANNEL_MENTIONS_REPLIES = "mentions_replies";
    public static final String ANDROID_CHANNEL_EVERYONE_HERE = "everyone_here";
    public static final String ANDROID_CHANNEL_URGENT_INCIDENTS = "urgent_incidents";
    // Notification category with the Reply and Mark as read actions; the app registers the same id.
    public static final String CATEGORY_CHAT_MESSAGE = "chat_message";
    // Lets an incident push through a Focus mode on iOS.
    public static final String INTERRUPTION_LEVEL_TIME_SENSITIVE = "time-sensitive";

    // --- Copy (English; the device locale is stored for localized copy later) ---
    public static final int BODY_MAX_CODE_POINTS = 180;
    public static final String BODY_ELLIPSIS = "\u2026";
    public static final String BODY_CHAT_FORMAT = "%s: %s";
    public static final String BODY_ATTACHMENT_FORMAT = "%s sent an attachment in %s";
    public static final String BODY_MENTION_HIDDEN_FORMAT = "New mention in %s";
    public static final String BODY_REPLY_HIDDEN_FORMAT = "New reply in %s";
    public static final String BODY_EVERYONE_HIDDEN_FORMAT = "New message for everyone in %s";
    public static final String BODY_HERE_HIDDEN_FORMAT = "New message for everyone here in %s";
    public static final String BODY_REVIEW_HIDDEN_FORMAT = "Review requested in %s";
    public static final String TITLE_INCIDENT_FORMAT = "%s incident on %s";
    public static final String BODY_INCIDENT_FORMAT = "%s %s";
    public static final String TITLE_NUDGE = "Daily update";
    public static final String BODY_NUDGE_FORMAT = "%s reminds you to post today's update. Deadline %s.";

    // --- Dispatch ---
    public static final String EXECUTOR_BEAN_NAME = "pushExecutor";
    public static final String EXECUTOR_THREAD_PREFIX = "push-";

    // --- Receipts ---
    // Expo advises reading receipts about 15 minutes after the send and keeps them for 24 hours.
    public static final String RECEIPT_POLL_INTERVAL = "PT15M";
    public static final Duration RECEIPT_DELAY = Duration.ofMinutes(15);
    public static final Duration RECEIPT_RETENTION = Duration.ofHours(24);
    // Bounds the memory of tickets waiting for a receipt; beyond it a ticket is not followed up.
    public static final int RECEIPT_TRACKING_LIMIT = 50_000;
}
