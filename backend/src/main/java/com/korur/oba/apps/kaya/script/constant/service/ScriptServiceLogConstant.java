package com.korur.oba.apps.kaya.script.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the script services, including the not-found line.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ScriptServiceLogConstant {

    private ScriptServiceLogConstant() {
    }

    // --- Reads ---
    public static final String READING_SCRIPT = "Reading script {} of organization {}";
    public static final String LOADING_VERSION_SCRIPTS = "Loading the scripts of version {}";
    public static final String SCRIPT_NOT_FOUND = "Script {} not found in organization {}";

    // --- Writes ---
    public static final String SCRIPTS_REPLACED = "Replaced the scripts of version {} in organization {}: {} removed, {} written";
    public static final String SCRIPT_PART_SAVED = "Saved the script part {} of version {} in organization {}";
    public static final String SCRIPT_PARTS_DELETED = "Deleted {} unlinked script parts of version {} in organization {}";
}
