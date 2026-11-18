package com.korur.oba.apps.kaya.toolparameter.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the tool parameter file services, including the not-found line.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ToolParameterServiceLogConstant {

    private ToolParameterServiceLogConstant() {
    }

    // --- Reads ---
    public static final String READING_TOOL_PARAMETER = "Reading tool parameter file {} of organization {}";
    public static final String LOADING_VERSION_TOOL_PARAMETERS = "Loading the tool parameter files of version {}";
    public static final String TOOL_PARAMETER_NOT_FOUND = "ToolParameter {} not found in organization {}";

    // --- Writes ---
    public static final String TOOL_PARAMETERS_REPLACED = "Replaced the tool parameter files of version {} in organization {}: {} removed, {} written";
    public static final String TOOL_PARAMETER_PART_SAVED = "Saved the tool parameter file part {} of version {} in organization {}";
    public static final String TOOL_PARAMETER_PARTS_DELETED = "Deleted {} unlinked tool parameter file parts of version {} in organization {}";
}
