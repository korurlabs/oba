package com.korur.oba.apps.kaya.skill.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, limits and error value keys of the skill domain.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SkillConstant {

    private SkillConstant() {
    }

    // --- Entity fields (specifications and sorting) ---
    public static final String FIELD_ID = "id";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_VERSION_ID = "versionId";
    public static final String FIELD_NAME = "name";

    // --- Error value keys ---
    public static final String FIELD_SKILL_ID = "skillId";
    public static final String FIELD_PART_KIND = "partKind";
    public static final String FIELD_PART_ID = "partId";

    // --- Limits ---
    public static final int VERSION_IDS_MAX = 20;
    public static final String VERSION_IDS_SIZE_MESSAGE = "must name 1 to 20 version ids";
}
