package com.korur.oba.apps.kaya.pack.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, the name pattern and the name and description limits for packs.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PackConstant {

    private PackConstant() {
    }

    public static final String FIELD_PACK_ID = "packId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_PARENT_PACK_ID = "parentPackId";
    public static final String FIELD_CONDITION = "condition";

    // The conditions under which a pack is in use and keeps its name, named in a rename refusal.
    public static final String CONDITION_REQUIRED = "REQUIRED";
    public static final String CONDITION_COMPOSED = "COMPOSED";
    public static final String CONDITION_VERSIONED = "VERSIONED";
    public static final String CONDITION_DRAFT_NAMESPACE_FILES = "DRAFT_NAMESPACE_FILES";
    public static final String CONDITION_PLANS = "PLANS";
    public static final String NAMESPACE_NAME_SEPARATOR = "-";
    public static final String PATH_SEPARATOR = "/";

    public static final int NAME_MAX_LENGTH = 60;
    public static final String NAME_PATTERN = "^[a-z0-9-]{1,60}$";
    public static final String NAME_INVALID_MESSAGE = "must be 1 to 60 lowercase letters, digits or hyphens";

    public static final int DESCRIPTION_MAX_LENGTH = 1000;
    public static final String DESCRIPTION_BLANK_MESSAGE = "Description must not be blank; send null for none";
    public static final String DESCRIPTION_SIZE_MESSAGE = "Description must not exceed 1000 characters";
}
