package com.korur.oba.apps.kaya.settings.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the settings services, including the not-found lines.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SettingsServiceLogConstant {

    private SettingsServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LISTING_SETTINGS = "Listing settings of organization {} with {}";
    public static final String READING_SETTINGS = "Reading settings {} of organization {}";
    public static final String LOADING_VERSION_SETTINGS = "Loading the settings of version {}";
    public static final String SETTINGS_NOT_FOUND = "Settings {} not found in organization {}";
    public static final String SETTINGS_UNREADABLE = "Settings {} of version {} are not a JSON object; showing no rules or hooks";

    // --- Writes ---
    public static final String SETTINGS_REPLACED = "Replaced the settings of version {} in organization {}: {} removed, {} written";
}
