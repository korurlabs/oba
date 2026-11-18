package com.korur.oba.apps.kaya.subagent.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the subagent services, including the not-found lines.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SubagentServiceLogConstant {

    private SubagentServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LISTING_SUBAGENTS = "Listing subagents of organization {} with {}";
    public static final String READING_SUBAGENT = "Reading subagent {} of organization {}";
    public static final String LOADING_VERSION_SUBAGENTS = "Loading the subagents of version {}";
    public static final String SUBAGENT_NOT_FOUND = "Subagent {} not found in organization {}";

    // --- Writes ---
    public static final String SUBAGENTS_REPLACED = "Replaced the subagents of version {} in organization {}: {} removed, {} written";
    public static final String SUBAGENT_SAVED = "Saved the subagent {} of version {} in organization {}: {} old row removed";
    public static final String SUBAGENT_DELETED = "Deleted the subagent {} of version {} in organization {}: {} row removed";
}
