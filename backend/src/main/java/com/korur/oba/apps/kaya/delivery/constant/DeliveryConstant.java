package com.korur.oba.apps.kaya.delivery.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Limits, field names and ETag parts of the delivery endpoints the CLI calls.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DeliveryConstant {

    private DeliveryConstant() {
    }

    // --- Request limits ---
    public static final int REMOTE_URL_MAX_LENGTH = 500;
    public static final int NUMBER_MIN = 1;
    public static final String SHA256_PATTERN = "^[0-9a-f]{64}$";

    // --- Validation messages ---
    public static final String REMOTE_URL_REQUIRED_MESSAGE = "Remote URL is required";
    public static final String REMOTE_URL_SIZE_MESSAGE = "Remote URL must not exceed 500 characters";
    public static final String REMOTE_URL_INVALID_MESSAGE = "Remote URL is not a git remote Kaya accepts";
    public static final String NUMBER_REQUIRED_MESSAGE = "Release number is required";
    public static final String NUMBER_MIN_MESSAGE = "Release number must be at least 1";
    public static final String SHA256_INVALID_MESSAGE = "must be 64 lower-case hex characters";
    public static final int PROPOSAL_FILES_MIN = 1;
    public static final int PROPOSAL_FILES_MAX = 200;
    public static final String PROPOSAL_FILES_SIZE_MESSAGE = "A proposal holds 1 to 200 files";
    public static final String PROPOSAL_FILE_REQUIRED_MESSAGE = "A file of a proposal must not be null";

    // --- Error value keys ---
    public static final String FIELD_REMOTE_URL = "remoteUrl";
    public static final String FIELD_REPOSITORY_ID = "repositoryId";
    public static final String FIELD_RELEASE_ID = "releaseId";
    public static final String FIELD_SHA256 = "sha256";
    public static final String FIELD_PATHS = "paths";
    public static final String FIELD_OWNERS = "owners";
    public static final String FIELD_CANDIDATES = "candidates";

    // --- Review link of a proposal ---
    public static final String WEB_URL_DEFAULT = "https://kaya.korur.com";
    public static final String REVIEW_PATH = "/changes/";
    public static final String URL_PATH_SEPARATOR = "/";

    // --- Hashing and the ETag of the current release ---
    public static final String SHA256_ALGORITHM = "SHA-256";
    // Stands for the manifest hash in the ETag while the repository has no release yet.
    public static final String ETAG_NO_RELEASE = "none";
    // Content is addressed by its hash and never changes, but it is served only to a valid token,
    // so no shared cache may keep it.
    public static final String BLOB_CACHE_CONTROL = "private, max-age=31536000, immutable";
}
