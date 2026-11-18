package com.korur.oba.apps.kaya.convention.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, limits and error value keys of the convention domain.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ConventionConstant {

    private ConventionConstant() {
    }

    // --- Entity fields (specifications and sorting) ---
    public static final String FIELD_ID = "id";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_VERSION_ID = "versionId";
    public static final String FIELD_RELATIVE_PATH = "relativePath";

    // --- Error value keys ---
    public static final String FIELD_CONVENTION_ID = "conventionId";

    // --- Limits ---
    public static final int VERSION_IDS_MAX = 20;
    public static final String VERSION_IDS_SIZE_MESSAGE = "must name 1 to 20 version ids";
}
