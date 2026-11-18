package com.korur.oba.apps.kaya.convention.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the convention services, including the not-found lines.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ConventionServiceLogConstant {

    private ConventionServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LISTING_CONVENTIONS = "Listing conventions of organization {} with {}";
    public static final String READING_CONVENTION = "Reading convention {} of organization {}";
    public static final String LOADING_VERSION_CONVENTIONS = "Loading the conventions of version {}";
    public static final String CONVENTION_NOT_FOUND = "Convention {} not found in organization {}";

    // --- Writes ---
    public static final String CONVENTIONS_REPLACED = "Replaced the conventions of version {} in organization {}: {} removed, {} written";
    public static final String CONVENTION_SAVED = "Saved the convention {} of version {} in organization {}: {} old row removed";
    public static final String CONVENTION_DELETED = "Deleted the convention {} of version {} in organization {}: {} row removed";
    public static final String CONVENTION_PART_SAVED = "Saved the convention part {} of version {} in organization {}";
    public static final String CONVENTION_PARTS_DELETED = "Deleted {} unlinked convention parts of version {} in organization {}";
}
