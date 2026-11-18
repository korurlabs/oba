package com.korur.oba.apps.kaya.version.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, limits, messages and error value keys of versions, their drafts, diffs, rebases and
 * submits.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class VersionConstant {

    private VersionConstant() {
    }

    // --- Error value keys ---
    public static final String FIELD_VERSION_ID = "versionId";
    public static final String FIELD_CURRENT_VERSION_ID = "currentVersionId";
    public static final String FIELD_IN_REVIEW_VERSION_ID = "inReviewVersionId";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_PROPOSED_BY = "proposedBy";
    public static final String FIELD_PATH = "path";
    public static final String FIELD_PATHS = "paths";
    public static final String FIELD_MODE = "mode";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_CONFLICTS = "conflicts";
    public static final String FIELD_PACK_ID = "packId";
    public static final String FIELD_BASE_VERSION_ID = "baseVersionId";
    public static final String FIELD_BASE_PACK_ID = "basePackId";
    public static final String FIELD_PARENT_VERSION_ID = "parentVersionId";

    // --- Entity attributes, for specifications ---
    public static final String ATTRIBUTE_ORGANIZATION_ID = "organizationId";
    public static final String ATTRIBUTE_PACK_ID = "packId";
    public static final String ATTRIBUTE_STATUS = "status";
    public static final String ATTRIBUTE_NUMBER = "number";
    public static final String ATTRIBUTE_UPDATED_AT = "updatedAt";
    public static final String ATTRIBUTE_ID = "id";
    public static final String ATTRIBUTE_PROPOSED_BY = "proposedBy";

    // --- Request limits ---
    public static final int REASON_MIN_LENGTH = 10;
    public static final int REASON_MAX_LENGTH = 1000;
    public static final String REASON_REQUIRED_MESSAGE = "A reason is required";
    public static final String REASON_SIZE_MESSAGE = "The reason must be 10 to 1000 characters";
    // Base64 of 5 MiB; the decoded bytes are held to the 5 MiB file limit by the file rules.
    public static final int CONTENT_MAX_LENGTH = 6_990_508;
    public static final String CONTENT_SIZE_MESSAGE = "The content must not exceed 5 MiB";
    public static final String CONTENT_REQUIRED_MESSAGE = "Content is required";
    public static final String EXECUTABLE_NOT_ALLOWED_REASON =
            "Only a hook or a file of a skill directory other than SKILL.md can be executable";
    public static final String CONTENT_NOT_BASE64_MESSAGE = "The content is not valid base64";
    public static final String PATH_REQUIRED_MESSAGE = "A path is required";
    public static final String MODE_REQUIRED_MESSAGE = "A mode is required";
    public static final int RESOLUTIONS_MAX = 5000;
    public static final String SHA256_ALGORITHM = "SHA-256";

    // --- Override markers ---
    public static final String OVERRIDE_NOT_OWN_MESSAGE =
            "A pack file cannot start overriding an inherited file; only a marker the file already carries is kept";

    // --- Item overrides ---
    // A skill item is its directory, which ends in the separator; any other item is a file path.
    public static final String ITEM_DIRECTORY_SUFFIX = "/";
    public static final String ANCESTOR_STILL_DELIVERS_MESSAGE =
            "An ancestor pack still delivers the item; delete the draft's item to inherit it again";

    // --- Unified diff ---
    public static final int DIFF_CONTEXT_LINES = 3;
    // Above this many line pairs the changed middle of a file is shown as removed and added whole.
    public static final long DIFF_MAX_CELLS = 2_000_000L;
    public static final String DIFF_OLD_PREFIX = "--- a/";
    public static final String DIFF_NEW_PREFIX = "+++ b/";
    public static final String DIFF_OLD_NONE = "--- /dev/null";
    public static final String DIFF_NEW_NONE = "+++ /dev/null";
    public static final String DIFF_HUNK_HEADER = "@@ -%d,%d +%d,%d @@";
    public static final String DIFF_NO_NEWLINE = "\\ No newline at end of file";
    public static final char DIFF_CONTEXT = ' ';
    public static final char DIFF_REMOVED = '-';
    public static final char DIFF_ADDED = '+';
    public static final String LINE_BREAK = "\n";
}
