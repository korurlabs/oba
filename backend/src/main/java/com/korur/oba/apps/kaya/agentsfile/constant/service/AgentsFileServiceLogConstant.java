package com.korur.oba.apps.kaya.agentsfile.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the agents file services, including the not-found lines.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AgentsFileServiceLogConstant {

    private AgentsFileServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LISTING_AGENTS_FILES = "Listing agents files of organization {} with {}";
    public static final String READING_AGENTS_FILE = "Reading agents file {} of organization {}";
    public static final String LOADING_VERSION_AGENTS_FILES = "Loading the agents files of version {}";
    public static final String AGENTS_FILE_NOT_FOUND = "Agents file {} not found in organization {}";

    // --- Writes ---
    public static final String AGENTS_FILES_REPLACED = "Replaced the agents files of version {} in organization {}: {} removed, {} written";
    public static final String AGENTS_FILE_SAVED = "Saved the agents file {} of version {} in organization {}: {} old row removed";
    public static final String AGENTS_FILE_DELETED = "Deleted the agents file {} of version {} in organization {}: {} row removed";
}
