package com.korur.oba.apps.kaya.session.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, limits and patterns of the session domain.
 */
public final class SessionConstant {

    private SessionConstant() {
    }

    // --- Error value and audit detail keys ---
    public static final String FIELD_SESSION_ID = "sessionId";
    public static final String FIELD_SESSION_ROW_ID = "sessionRowId";
    public static final String FIELD_SESSION_ROW_IDS = "sessionRowIds";
    public static final String FIELD_SESSIONS = "sessions";
    public static final String FIELD_USER_ID = "userId";

    // --- Column limits ---
    public static final int TITLE_MAX_LENGTH = 500;
    public static final int SESSION_TITLE_MAX_LENGTH = 200;
    public static final int FIRST_PROMPT_MAX_LENGTH = 500;
    public static final int BRANCH_MAX_LENGTH = 255;
    public static final int CWD_MAX_LENGTH = 500;
    public static final int MODEL_MAX_LENGTH = 100;
    public static final int CLAUDE_CODE_VERSION_MAX_LENGTH = 40;
    public static final int EMAIL_MAX_LENGTH = 320;
    public static final int ACCOUNT_ORGANIZATION_UUID_MAX_LENGTH = 36;
    public static final int ACCOUNT_ORGANIZATION_NAME_MAX_LENGTH = 200;
    public static final int ACCOUNT_FIELD_MAX_LENGTH = 60;
    public static final int FEATURE_MAX_LENGTH = 200;
    public static final int EVENT_NAME_MAX_LENGTH = 40;
    public static final int AGENT_ID_MAX_LENGTH = 100;
    public static final int SUBAGENT_TYPE_MAX_LENGTH = 100;
    public static final int DESCRIPTION_MAX_LENGTH = 500;
    public static final int SKILL_MAX_LENGTH = 100;
    public static final int HANDOFF_PATH_MAX_LENGTH = 500;
    public static final int HANDOFF_CONTENT_MAX_LENGTH = 200_000;
    public static final int LINK_REFERENCE_MAX_LENGTH = 500;
    public static final int LINK_URL_MAX_LENGTH = 500;

    // --- Links ---
    public static final String KAZAN_TICKET_PATTERN = "[A-Z][A-Z0-9]+-[0-9]+";
    public static final String PULL_REQUEST_URL_PREFIX = "https://github.com/";
    public static final String TICKET_URL_KEY_PLACEHOLDER = "{key}";
    // A pull request URL as the recorder copies it from the output of gh pr create.
    public static final String PULL_REQUEST_URL_PATTERN = "^https://github\\.com/[^/\\s]+/[^/\\s]+/pull/[0-9]+$";

    // --- Event keys of the stored rows ---
    public static final String EVENT_SEQ = "seq";
    public static final String EVENT_AT = "at";
    public static final String EVENT_NAME = "event";
    public static final String EVENT_AGENT_ID = "agent_id";
    public static final String EVENT_PAYLOAD = "payload";
    public static final String EVENT_URL = "url";

    // --- Truncated field names, for the log ---
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_SESSION_TITLE = "sessionTitle";
    public static final String FIELD_FIRST_PROMPT = "firstPrompt";
    public static final String FIELD_BRANCH = "branch";
    public static final String FIELD_CWD = "cwd";
    public static final String FIELD_MODEL = "model";
    public static final String FIELD_CLAUDE_CODE_VERSION = "claudeCodeVersion";
    public static final String FIELD_ACCOUNT = "account";
    public static final String FIELD_GIT_AUTHOR_EMAIL = "gitAuthorEmail";
    public static final String FIELD_FEATURE = "feature";
    public static final String FIELD_SUBAGENT = "subagent";
    public static final String FIELD_SKILL = "skill";

    // --- Entity field names, for specifications and sorting ---
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_REPOSITORY_ID = "repositoryId";
    public static final String FIELD_MACHINE_ID = "machineId";
    public static final String FIELD_ACCOUNT_ORGANIZATION_UUID = "accountOrganizationUuid";
    public static final String FIELD_ENDED = "ended";
    public static final String FIELD_LAST_EVENT_AT = "lastEventAt";
    public static final String FIELD_SESSION = "session";
    public static final String FIELD_KIND = "kind";
    public static final String FIELD_REFERENCE = "reference";
    public static final String FIELD_LINK_ID = "linkId";
    public static final String FIELD_RULE = "rule";
    public static final String FIELD_PATH = "path";

    // --- Handoff snapshots ---
    // A feature's handoffs lie below handoffs/{feature}/, so their paths start with the feature and this.
    public static final String HANDOFF_DIRECTORY_SEPARATOR = "/";

    // --- Session list ---
    public static final int LIST_DEFAULT_AMOUNT = 60;
    public static final int LIST_MAX_AMOUNT = 60;
    public static final int SEARCH_MAX_LENGTH = 200;
    public static final String SEARCH_SIZE_MESSAGE = "Search must be at most 200 characters";
    public static final String AMOUNT_MAX_MESSAGE = "amount cannot exceed 60";
    public static final String SESSION_ID_PATTERN = "[A-Za-z0-9_-]{1,100}";
    public static final String LIKE_WILDCARD = "%";
    public static final char LIKE_ESCAPE = '\\';
    public static final String LIKE_SPECIAL_CHARACTERS_PATTERN = "([\\\\%_])";
    public static final String LIKE_ESCAPED_REPLACEMENT = "\\\\$1";

    // --- Manual links ---
    public static final String LINK_KIND_REQUIRED_MESSAGE = "kind is required";
    public static final String LINK_REFERENCE_REQUIRED_MESSAGE = "reference is required";
    public static final String LINK_REFERENCE_SIZE_MESSAGE = "reference must be at most 500 characters";
    public static final String LINK_RULE_KAZAN_TICKET = "KAZAN_TICKET_KEY";
    public static final String LINK_RULE_PULL_REQUEST = "PULL_REQUEST_URL";

    // --- Live stream ---
    public static final String STREAM_EVENT_SESSION_UPDATED = "session-updated";
    public static final String STREAM_EVENT_HEARTBEAT = "heartbeat";
    public static final String STREAM_FIELD_AT = "at";
}
