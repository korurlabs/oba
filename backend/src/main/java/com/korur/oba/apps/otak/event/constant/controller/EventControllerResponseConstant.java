package com.korur.oba.apps.otak.event.constant.controller;

/**
 * Response message constants for event controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class EventControllerResponseConstant {

    private EventControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_EVENT_LIST_RETRIEVED = "Events retrieved successfully";
    public static final String RESPONSE_EVENT_CREATED = "Event created successfully";
    public static final String RESPONSE_EVENT_RETRIEVED = "Event retrieved successfully";
    public static final String RESPONSE_EVENT_UPDATED = "Event updated successfully";
    public static final String RESPONSE_EVENT_DELETED = "Event deleted successfully";
}
