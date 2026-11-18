package com.korur.oba.apps.kaya.version.constant.controller;

/**
 * Response messages of the version and draft controllers.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class VersionControllerResponseConstant {

    private VersionControllerResponseConstant() {
    }

    public static final String RESPONSE_VERSIONS_RETRIEVED = "Versions retrieved successfully";
    public static final String RESPONSE_VERSION_RETRIEVED = "Version retrieved successfully";
    public static final String RESPONSE_FILE_RETRIEVED = "Version file retrieved successfully";
    public static final String RESPONSE_DIFF_RETRIEVED = "Version diff retrieved successfully";
    public static final String RESPONSE_ROLLBACK_CREATED = "Rollback draft created successfully";
    public static final String RESPONSE_DRAFT_CREATED = "Draft created successfully";
    public static final String RESPONSE_FILE_SAVED = "Draft file saved successfully";
    public static final String RESPONSE_FILE_DELETED = "Draft file deleted successfully";
    public static final String RESPONSE_DRAFT_DELETED = "Draft deleted successfully";
    public static final String RESPONSE_DRAFT_REBASED = "Draft rebased successfully";
    public static final String RESPONSE_OVERRIDE_SET = "Override base set successfully";
    public static final String RESPONSE_OVERRIDE_CLEARED = "Override cleared successfully";
}
