package com.korur.oba.apps.kalkan.page.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Response messages of the page controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class KalkanPageControllerResponseConstant {

    private KalkanPageControllerResponseConstant() {
    }

    public static final String RESPONSE_PAGES_RETRIEVED = "Pages retrieved successfully";
    public static final String RESPONSE_PAGE_RETRIEVED = "Page retrieved successfully";
    public static final String RESPONSE_PAGE_CREATED = "Page created successfully";
    public static final String RESPONSE_PAGE_UPDATED = "Page updated successfully";
    public static final String RESPONSE_PAGE_MOVED = "Page moved successfully";
    public static final String RESPONSE_PAGE_DELETED = "Page deleted successfully";
    public static final String RESPONSE_PAGE_RESTORED = "Page restored successfully";
    public static final String RESPONSE_PAGE_UPSERTED = "Page upserted successfully";
}
