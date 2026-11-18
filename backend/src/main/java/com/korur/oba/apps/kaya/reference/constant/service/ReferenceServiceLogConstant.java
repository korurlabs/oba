package com.korur.oba.apps.kaya.reference.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the reference services, including the not-found line.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ReferenceServiceLogConstant {

    private ReferenceServiceLogConstant() {
    }

    // --- Reads ---
    public static final String READING_REFERENCE = "Reading reference {} of organization {}";
    public static final String LOADING_VERSION_REFERENCES = "Loading the references of version {}";
    public static final String REFERENCE_NOT_FOUND = "Reference {} not found in organization {}";

    // --- Writes ---
    public static final String REFERENCES_REPLACED = "Replaced the references of version {} in organization {}: {} removed, {} written";
    public static final String REFERENCE_PART_SAVED = "Saved the reference part {} of version {} in organization {}";
    public static final String REFERENCE_PARTS_DELETED = "Deleted {} unlinked reference parts of version {} in organization {}";
}
