package com.korur.oba.apps.kaya.ingest.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Limits, field names and messages of the ingest endpoint.
 */
public final class IngestConstant {

    private IngestConstant() {
    }

    // --- Request limits ---
    public static final int SESSION_ID_MAX_LENGTH = 100;
    public static final String SESSION_ID_PATTERN = "^[A-Za-z0-9_-]+$";
    public static final int EVENTS_MAX = 500;
    public static final int HANDOFFS_MAX = 50;
    public static final int REMOTE_URL_MAX_LENGTH = 500;
    public static final int REPOSITORY_NAME_MAX_LENGTH = 100;
    public static final int MACHINE_NAME_MAX_LENGTH = 255;
    public static final int HANDOFF_PATH_MAX_LENGTH = 500;
    public static final int HANDOFF_CONTENT_MIN_LENGTH = 1;
    public static final int HANDOFF_CONTENT_MAX_LENGTH = 200_000;
    public static final int PLANS_MAX = 20;
    public static final int PLAN_PATH_MAX_LENGTH = 500;
    public static final int PLAN_CONTENT_MIN_LENGTH = 1;
    public static final int PLAN_CONTENT_MAX_LENGTH = 200_000;
    // A spec file: repository-relative, below a plans/ folder somewhere under docs/, a markdown file.
    public static final String PLAN_PATH_PATTERN = "^docs/(.+/)?plans/.+\\.md$";
    public static final String PATH_SEPARATOR = "/";
    public static final String PATH_SEGMENT_CURRENT = ".";
    public static final String PATH_SEGMENT_PARENT = "..";
    public static final int EMAIL_MAX_LENGTH = 320;
    // Batches a token may send per minute; a recorder sends one every few seconds at most.
    public static final int BATCHES_PER_MINUTE = 120;
    public static final long RATE_WINDOW_SECONDS = 60;
    // Windows of tokens idle for a while are dropped once the table holds this many.
    public static final int RATE_TABLE_PRUNE_SIZE = 10_000;

    // --- Event keys the endpoint checks ---
    public static final String EVENT_SEQ = "seq";
    public static final String EVENT_AT = "at";
    public static final String EVENT_NAME = "event";
    public static final String EVENT_AGENT_ID = "agent_id";
    public static final String EVENT_PAYLOAD = "payload";
    public static final String EVENT_URL = "url";
    public static final int EVENT_NAME_MAX_LENGTH = 40;
    public static final int EVENT_AGENT_ID_MAX_LENGTH = 100;

    // --- Error value keys ---
    public static final String FIELD_INDEX = "index";
    public static final String FIELD_FIELD = "field";
    public static final String FIELD_SESSION_ID = "sessionId";
    public static final String FIELD_TOKEN_ID = "tokenId";
    public static final String FIELD_LIMIT = "limit";
    public static final String FIELD_GIT_AUTHOR_EMAIL = "gitAuthorEmail";

    // --- Validation messages ---
    public static final String SESSION_ID_REQUIRED_MESSAGE = "Session id is required";
    public static final String SESSION_ID_SIZE_MESSAGE = "Session id must not exceed 100 characters";
    public static final String SESSION_ID_FORMAT_MESSAGE = "Session id may hold only letters, digits, '_' and '-'";
    public static final String REPOSITORY_REQUIRED_MESSAGE = "Repository is required";
    public static final String REMOTE_URL_REQUIRED_MESSAGE = "Remote URL is required";
    public static final String REMOTE_URL_SIZE_MESSAGE = "Remote URL must not exceed 500 characters";
    public static final String REPOSITORY_NAME_SIZE_MESSAGE = "Repository name must not exceed 100 characters";
    public static final String MACHINE_REQUIRED_MESSAGE = "Machine is required";
    public static final String MACHINE_NAME_REQUIRED_MESSAGE = "Machine name is required";
    public static final String MACHINE_NAME_SIZE_MESSAGE = "Machine name must not exceed 255 characters";
    public static final String SUMMARY_REQUIRED_MESSAGE = "Summary is required";
    public static final String EVENTS_SIZE_MESSAGE = "A batch holds at most 500 events";
    public static final String HANDOFFS_SIZE_MESSAGE = "A batch holds at most 50 handoffs";
    public static final String HANDOFF_PATH_REQUIRED_MESSAGE = "Handoff path is required";
    public static final String HANDOFF_PATH_SIZE_MESSAGE = "Handoff path must not exceed 500 characters";
    public static final String HANDOFF_CONTENT_REQUIRED_MESSAGE = "Handoff content is required";
    public static final String HANDOFF_CONTENT_SIZE_MESSAGE = "Handoff content must be 1 to 200000 characters";
    public static final String HANDOFF_CAPTURED_AT_REQUIRED_MESSAGE = "Handoff capture time is required";
    public static final String PLANS_SIZE_MESSAGE = "A batch holds at most 20 plans";
    public static final String PLAN_KIND_REQUIRED_MESSAGE = "Plan kind is required";
    public static final String PLAN_SOURCE_REQUIRED_MESSAGE = "Plan source is required";
    public static final String PLAN_PATH_SIZE_MESSAGE = "Plan path must not exceed 500 characters";
    public static final String PLAN_PATH_FOR_KIND_MESSAGE = "A SPEC plan needs a repository-relative path below "
            + "docs/**/plans/ ending in .md; a SESSION plan has no path";
    public static final String PLAN_SOURCE_FOR_KIND_MESSAGE = "A SPEC plan comes from a FILE; a SESSION plan from "
            + "PLAN_MODE or a MESSAGE";
    public static final String PLAN_CONTENT_REQUIRED_MESSAGE = "Plan content is required";
    public static final String PLAN_CONTENT_SIZE_MESSAGE = "Plan content must be 1 to 200000 characters";
    public static final String PLAN_CAPTURED_AT_REQUIRED_MESSAGE = "Plan capture time is required";
}
