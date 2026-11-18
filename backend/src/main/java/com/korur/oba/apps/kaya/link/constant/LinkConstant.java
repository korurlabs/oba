package com.korur.oba.apps.kaya.link.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names and messages of the links between delivered Markdown files.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class LinkConstant {

    private LinkConstant() {
    }

    // --- Field names and error value keys ---
    public static final String FIELD_VERSION_ID = "versionId";
    public static final String FIELD_REPOSITORY_ID = "repositoryId";
    public static final String FIELD_PATH = "path";

    // --- Request limits ---
    public static final int VERSION_IDS_MAX = 20;
    public static final String VERSION_IDS_SIZE_MESSAGE = "must name 1 to 20 version ids";
    public static final String PATH_REQUIRED_MESSAGE = "A path is required";

    // --- Refusal messages ---
    public static final String SCOPE_REQUIRED_MESSAGE = "Give a versionId, a repositoryId, or both";
    public static final String VERSION_NOT_OPEN_MESSAGE = "The version is %s; only a DRAFT or IN_REVIEW version is composed in place";
    public static final String VERSION_NOT_COMPOSED_MESSAGE = "The version is of a pack this repository does not compose";
}
