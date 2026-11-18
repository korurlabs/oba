package com.korur.oba.apps.kaya.hook.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the hook services, including the not-found lines.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class HookServiceLogConstant {

    private HookServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LISTING_HOOKS = "Listing hooks of organization {} with {}";
    public static final String READING_HOOK = "Reading hook {} of organization {}";
    public static final String LOADING_VERSION_HOOKS = "Loading the hooks of version {}";
    public static final String HOOK_NOT_FOUND = "Hook {} not found in organization {}";

    // --- Writes ---
    public static final String HOOKS_REPLACED = "Replaced the hooks of version {} in organization {}: {} removed, {} written";
    public static final String HOOK_SAVED = "Saved the hook {} of version {} in organization {}: {} old row removed";
    public static final String HOOK_DELETED = "Deleted the hook {} of version {} in organization {}: {} row removed";
}
