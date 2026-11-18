package com.korur.oba.apps.kaya.role.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the role services, including the not-found lines.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RoleServiceLogConstant {

    private RoleServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LISTING_ROLES = "Listing roles of organization {} with {}";
    public static final String READING_ROLE = "Reading role {} of organization {}";
    public static final String LOADING_VERSION_ROLES = "Loading the roles of version {}";
    public static final String ROLE_NOT_FOUND = "Role {} not found in organization {}";

    // --- Writes ---
    public static final String ROLES_REPLACED = "Replaced the roles of version {} in organization {}: {} removed, {} written";
    public static final String ROLE_SAVED = "Saved the role {} of version {} in organization {}: {} old row removed";
    public static final String ROLE_DELETED = "Deleted the role {} of version {} in organization {}: {} row removed";
}
