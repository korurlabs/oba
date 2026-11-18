package com.korur.oba.apps.kalkan.page.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the page controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class KalkanPageControllerLogConstant {

    private KalkanPageControllerLogConstant() {
    }

    public static final String PAGE_TREE_REQUEST = "Received request from user {} to list the page tree of organization {}";
    public static final String PAGE_TREE_COMPLETED = "Listed {} pages of organization {}";
    public static final String PAGE_GET_REQUEST = "Received request from user {} to read page {} in organization {}";
    public static final String PAGE_GET_COMPLETED = "Read page {} in organization {}";
    public static final String PAGE_CREATE_REQUEST = "Received request from user {} to create page {} under parent {} in organization {}";
    public static final String PAGE_CREATE_COMPLETED = "Created page {} in organization {}";
    public static final String PAGE_UPDATE_REQUEST = "Received request from user {} to update page {} in organization {}";
    public static final String PAGE_UPDATE_COMPLETED = "Updated page {} in organization {} to version {}";
    public static final String PAGE_MOVE_REQUEST = "Received request from user {} to move page {} under parent {} in organization {}";
    public static final String PAGE_MOVE_COMPLETED = "Moved page {} in organization {}";
    public static final String PAGE_DELETE_REQUEST = "Received request from user {} to delete page {} in organization {}";
    public static final String PAGE_DELETE_COMPLETED = "Deleted page {} in organization {}";
    public static final String PAGE_RESTORE_REQUEST = "Received request from user {} to restore page {} in organization {}";
    public static final String PAGE_RESTORE_COMPLETED = "Restored page {} in organization {}";
    public static final String PAGE_UPSERT_REQUEST = "Received request from user {} to upsert path {} in organization {}, dry run {}";
    public static final String PAGE_UPSERT_COMPLETED = "Upserted path {} in organization {}: {}";
}
