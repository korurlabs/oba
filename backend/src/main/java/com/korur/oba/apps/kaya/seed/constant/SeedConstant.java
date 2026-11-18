package com.korur.oba.apps.kaya.seed.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, limits and messages of the seed.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SeedConstant {

    private SeedConstant() {
    }

    // --- Request limits ---
    public static final int SOURCE_REF_MAX_LENGTH = 100;
    public static final int SETS_MAX = 200;
    public static final int FILES_PER_SET_MAX = 5000;
    // Totals of one run, over every set: the run holds the organization's seed lock and the named
    // content in memory while it screens and imports, so a request is bounded as a whole as well.
    public static final int SEED_FILES_MAX = 20_000;
    public static final long SEED_CONTENT_MAX_BYTES = 67_108_864L;
    public static final int REPORTED_PATHS_MAX = 5000;
    public static final int REPORTED_PATH_MAX_LENGTH = 1000;
    public static final int REASON_MAX_LENGTH = 500;
    public static final int SET_NAME_MAX_LENGTH = 60;
    public static final int REMOTE_URL_MAX_LENGTH = 500;
    public static final int REPOSITORY_NAME_MAX_LENGTH = 100;
    public static final int REPOSITORIES_MAX = 200;

    // --- Field names in errorValues ---
    public static final String FIELD_SETS = "sets";
    public static final String FIELD_FILES = "files";
    public static final String FIELD_MISSING = "missing";
    public static final String FIELD_LIMIT_FILES = "limitFiles";
    public static final String FIELD_FILE_COUNT = "fileCount";
    public static final String FIELD_CONTENT_BYTES = "contentBytes";
    public static final String FIELD_PARENT = "parent";
    public static final String FIELD_REPOSITORIES = "repositories";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_STORED_PARENT_PACK_ID = "storedParentPackId";

    // --- Request refusals ---
    public static final String SET_LISTED_TWICE_MESSAGE = "the set %s %s is listed twice";
    public static final String PATH_LISTED_TWICE_MESSAGE = "the set %s lists %s twice";
    public static final String PARENT_IS_SELF_MESSAGE = "the pack %s names itself as its parent";
    public static final String PARENT_UNKNOWN_MESSAGE =
            "the pack %s names the parent %s, which neither the request nor the organization has";
    public static final String PARENT_CYCLE_MESSAGE = "the packs of the request form a parent cycle through %s";
    public static final String REPOSITORY_PACK_UNKNOWN_MESSAGE =
            "the repository %s uses the pack %s, which neither the request nor the organization has";
    public static final String REPOSITORY_LISTED_TWICE_MESSAGE = "the repository %s is listed twice";

    public static final String SET_KEY_SEPARATOR = ":";
    public static final String PARENT_ORDER_BROKEN_MESSAGE = "No pack of the seed could be created under its parent";

    // --- Links left out ---
    public static final String LINK_LEFT_OUT_REASON =
            "Left out of the %s %s: the symlink leads to %s, which is not delivered because this run refused %s";

    // --- Versions ---
    public static final String VERSION_REASON_FORMAT = "Seed of %s";
}
