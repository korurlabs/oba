package com.korur.oba.apps.kaya.toollink.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the tool link services.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ToolLinkServiceLogConstant {

    private ToolLinkServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LOADING_VERSION_TOOL_LINKS = "Loading the tool links of version {}";
    public static final String LOADING_VERSIONS_TOOL_LINKS = "Loading the tool links of {} versions of organization {}";

    // --- Writes ---
    public static final String TOOL_LINK_SAVED = "Saved the tool link {} of version {} in organization {}";
    public static final String TOOL_LINK_DELETED = "Deleted the tool link {} of version {} in organization {}: {} removed";
    public static final String TOOL_LINKS_REPLACED = "Replaced the tool links of version {} in organization {}: {} removed, {} written";
}
