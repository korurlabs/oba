package com.korur.oba.apps.kaya.settings.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, limits, error value keys and settings.json keys of the settings domain.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SettingsConstant {

    private SettingsConstant() {
    }

    // --- Entity fields (specifications and sorting) ---
    public static final String FIELD_ID = "id";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_VERSION_ID = "versionId";

    // --- Error value keys ---
    public static final String FIELD_SETTINGS_ID = "settingsId";

    // --- Keys of .claude/settings.json ---
    public static final String SETTINGS_PERMISSIONS = "permissions";
    public static final String SETTINGS_HOOKS = "hooks";
    public static final String SETTINGS_ALLOW = "allow";
    public static final String SETTINGS_ASK = "ask";
    public static final String SETTINGS_DENY = "deny";
    public static final String SETTINGS_MATCHER = "matcher";
    public static final String SETTINGS_TYPE = "type";
    public static final String SETTINGS_COMMAND = "command";

    // --- Limits ---
    public static final int VERSION_IDS_MAX = 20;
    public static final String VERSION_IDS_SIZE_MESSAGE = "must name 1 to 20 version ids";
}
