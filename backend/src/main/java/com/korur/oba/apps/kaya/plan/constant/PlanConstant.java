package com.korur.oba.apps.kaya.plan.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names and limits of plans, their versions and their feedback. The limits match the checks
 * of the plan schema, so a request the API accepts is never refused by the database.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PlanConstant {

    private PlanConstant() {
    }

    // --- Error value keys ---
    public static final String FIELD_PLAN_ID = "planId";
    public static final String FIELD_VERSION_NUMBER = "versionNumber";
    public static final String FIELD_LATEST_VERSION_NUMBER = "latestVersionNumber";
    public static final String FIELD_FEEDBACK_ID = "feedbackId";
    public static final String FIELD_DECISION = "decision";
    public static final String FIELD_COMMENT = "comment";
    public static final String FIELD_LINE_START = "lineStart";
    public static final String FIELD_LINE_END = "lineEnd";
    public static final String FIELD_LINE_COUNT = "lineCount";
    public static final String FIELD_SESSION_ID = "sessionId";
    public static final String FIELD_REMOTE_URL = "remoteUrl";
    public static final String FIELD_BRANCH = "branch";

    public static final String FIELD_REVIEWER_ID = "reviewerId";
    public static final String FIELD_QUERY = "query";

    // --- Entity attributes read by the list filters ---
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_REPOSITORY_ID = "repositoryId";
    public static final String FIELD_PACK_ID = "packId";
    public static final String FIELD_KIND = "kind";
    public static final String FIELD_REVIEW_STATE = "reviewState";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_PATH = "path";

    // --- Sorting ---
    public static final String FIELD_LAST_CHANGED_AT = "lastChangedAt";
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

    // --- Feedback validation messages ---
    public static final String VERSION_NUMBER_REQUIRED_MESSAGE = "versionNumber is required";
    public static final String VERSION_NUMBER_MIN_MESSAGE = "versionNumber must be at least 1";
    public static final String DECISION_REQUIRED_MESSAGE = "decision is required";
    public static final String COMMENT_SIZE_MESSAGE = "comment must be 1 to 4000 characters";
    public static final String LINE_MIN_MESSAGE = "Line numbers start at 1";
    public static final String QUOTE_SIZE_MESSAGE = "quote must be at most 500 characters";
    public static final String COMMENT_REQUIRED_MESSAGE = "A comment is required unless the feedback approves";
    public static final String LINE_BOUNDS_PAIRED_MESSAGE = "Give both lineStart and lineEnd, or neither";
    public static final String LINE_BOUNDS_ORDER_MESSAGE = "lineEnd must not be before lineStart";
    public static final String LINE_BOUNDS_RANGE_MESSAGE = "The line range ends after the last line of the version";

    // --- Handoffs of a plan's feature ---
    public static final String PLANNING_HANDOFF_FILE = "planning.md";
    public static final String HANDOFF_PATH_SEPARATOR = "/";
    public static final String SCOPE_GIVEN_HEADING = "## Scope given";
    public static final String SECTION_PREFIX = "## ";
    public static final String UNIT_NUMBER_PATTERN = "(?i)\\bunit\\s+(\\d{1,4})\\b";

    // --- Diff ---
    // The header path of a SESSION plan's diff, which has no file path of its own.
    public static final String SESSION_PLAN_DIFF_PATH = "session-plan.md";

    // --- Machine delivery of feedback ---
    public static final int DELIVERY_SESSION_IDS_MAX = 20;
    public static final int DELIVERY_ITEMS_MAX = 50;
    public static final int ACK_FEEDBACK_IDS_MIN = 1;
    public static final int ACK_FEEDBACK_IDS_MAX = 50;
    public static final int REMOTE_URL_MAX_LENGTH = 500;
    public static final int BRANCH_MAX_LENGTH = 255;
    public static final String DELIVERY_SESSION_IDS_SIZE_MESSAGE = "Give at most 20 session ids";
    public static final String REMOTE_URL_SIZE_MESSAGE = "Remote URL must not exceed 500 characters";
    public static final String BRANCH_SIZE_MESSAGE = "Branch must not exceed 255 characters";
    public static final String DELIVERY_QUERY_INVALID_MESSAGE = "Give either sessionId, or remoteUrl and branch";
    public static final String ACK_SESSION_ID_REQUIRED_MESSAGE = "sessionId is required";
    public static final String ACK_FEEDBACK_IDS_REQUIRED_MESSAGE = "feedbackIds is required";
    public static final String ACK_FEEDBACK_IDS_SIZE_MESSAGE = "Give 1 to 50 feedback ids";
    public static final String ACK_FEEDBACK_ID_NULL_MESSAGE = "A feedback id must not be null";
    // The web app's page of a plan, after the web app's origin.
    public static final String PLAN_PAGE_PATH = "/plans/";
    public static final String URL_PATH_SEPARATOR = "/";

    // --- Column limits ---
    public static final int PATH_MAX_LENGTH = 500;
    public static final int TITLE_MAX_LENGTH = 300;
    public static final int FEATURE_MAX_LENGTH = 100;
    public static final int CONTENT_MAX_LENGTH = 200_000;
    public static final int COMMENT_MAX_LENGTH = 4000;
    public static final int QUOTE_MAX_LENGTH = 500;
    public static final int DELIVERED_SESSION_ID_MAX_LENGTH = 100;
}
