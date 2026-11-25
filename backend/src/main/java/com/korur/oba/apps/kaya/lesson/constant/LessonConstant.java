package com.korur.oba.apps.kaya.lesson.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names and limits of lessons. The limits match the checks of the lesson schema, so a
 * request the API accepts is never refused by the database.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class LessonConstant {

    private LessonConstant() {
    }

    // --- Error value keys ---
    public static final String FIELD_LESSON_ID = "lessonId";
    public static final String FIELD_REMOTE_URL = "remoteUrl";
    public static final String FIELD_SINCE = "since";

    // --- Entity attributes read by the list filters ---
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_REPOSITORY_ID = "repositoryId";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_CLAUDE_SESSION_ID = "claudeSessionId";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_BODY = "body";

    // --- Sorting ---
    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String FIELD_ID = "id";

    // --- List paging and search ---
    public static final int LIST_DEFAULT_AMOUNT = 25;
    public static final int LIST_MAX_AMOUNT = 100;
    public static final String AMOUNT_MAX_MESSAGE = "amount cannot exceed 100";
    public static final int SEARCH_MAX_LENGTH = 200;
    public static final String SEARCH_SIZE_MESSAGE = "Search must be at most 200 characters";
    public static final String SESSION_ID_FILTER_MESSAGE = "sessionId must be a Claude Code session id";
    public static final String LIKE_WILDCARD = "%";
    public static final char LIKE_ESCAPE = '\\';
    public static final String LIKE_SPECIAL_CHARACTERS_PATTERN = "([\\\\%_])";
    public static final String LIKE_ESCAPED_REPLACEMENT = "\\\\$1";

    // --- Column limits ---
    public static final int TITLE_MIN_LENGTH = 1;
    public static final int TITLE_MAX_LENGTH = 300;
    public static final int BODY_MIN_LENGTH = 1;
    public static final int BODY_MAX_LENGTH = 4000;
    public static final int CLAUDE_SESSION_ID_MAX_LENGTH = 100;

    // --- Request validation messages ---
    public static final String TITLE_REQUIRED_MESSAGE = "title is required";
    public static final String TITLE_SIZE_MESSAGE = "title must be 1 to 300 characters";
    public static final String BODY_REQUIRED_MESSAGE = "body is required";
    public static final String BODY_SIZE_MESSAGE = "body must be 1 to 4000 characters";

    // --- Machine delivery ---
    // Caps the results of one search, so a broad search stays cheap however many lessons match.
    public static final int DELIVERY_ITEMS_MAX = 100;
    // One page of titles at session start. The default is the cap, so a CLI that sends no paging
    // still gets the newest 100 it always got.
    public static final int DELIVERY_DEFAULT_AMOUNT = 100;
    public static final int DELIVERY_MAX_AMOUNT = 100;
    public static final String FIELD_ETAG = "etag";
    // Names how the digest behind the ETag is computed. A CLI stores the ETag next to the file it
    // wrote, so a new computation takes a new form: an ETag the CLI stored under the old one then
    // never answers 304, and the CLI reads the whole list once.
    public static final String ETAG_FORM = "v2-";
    // The ETag of the lessons, bare or quoted as the ETag header carries it.
    public static final String ETAG_PATTERN = ETAG_FORM + "[0-9a-f]{64}|\"" + ETAG_FORM + "[0-9a-f]{64}\"";
    public static final String ETAG_QUOTE = "\"";
    public static final String ETAG_INVALID_MESSAGE = "etag must be the ETag header of page 1";
    public static final String ETAG_REQUIRED_MESSAGE = "etag is required from page 2 on";
    public static final int REMOTE_URL_MAX_LENGTH = 500;
    public static final String REMOTE_URL_REQUIRED_MESSAGE = "remoteUrl is required";
    public static final String REMOTE_URL_SIZE_MESSAGE = "Remote URL must not exceed 500 characters";
    public static final String REMOTE_URL_UNKNOWN_MESSAGE = "The organization has no repository with this remote";
    public static final String SEARCH_REQUIRED_MESSAGE = "search is required";
    public static final String CLAUDE_SESSION_ID_INVALID_MESSAGE = "claudeSessionId must be a Claude Code session id";
    public static final String SINCE_INVALID_MESSAGE = "since must be an ISO-8601 instant";
    // The web app's page of a lesson, after the web app's origin.
    public static final String LESSON_PAGE_PATH = "/lessons/";
    public static final String URL_PATH_SEPARATOR = "/";
}
