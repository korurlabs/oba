package com.korur.oba.apps.kaya.session.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Response messages of the session controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SessionControllerResponseConstant {

    private SessionControllerResponseConstant() {
    }

    public static final String RESPONSE_SESSIONS_RETRIEVED = "Sessions retrieved successfully";
    public static final String RESPONSE_FILTERS_RETRIEVED = "Session filters retrieved successfully";
    public static final String RESPONSE_SESSION_RETRIEVED = "Session retrieved successfully";
    public static final String RESPONSE_EVENTS_RETRIEVED = "Session events retrieved successfully";
    public static final String RESPONSE_HANDOFFS_RETRIEVED = "Session handoffs retrieved successfully";
    public static final String RESPONSE_LINK_ADDED = "Link added successfully";
    public static final String RESPONSE_LINK_REMOVED = "Link removed successfully";
    public static final String RESPONSE_SESSION_DELETED = "Session deleted successfully";
}
