package com.korur.oba.apps.otak.event.constant.controller;

/**
 * Log message constants for event controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class EventControllerLogConstant {

    private EventControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String EVENT_LIST_REQUEST = "Received request to list events";
    public static final String EVENT_LIST_COMPLETED = "List events request completed";

    public static final String EVENT_CREATE_REQUEST = "Received request to create event";
    public static final String EVENT_CREATE_COMPLETED = "Create event request completed";

    public static final String EVENT_GET_REQUEST = "Received request to get event";
    public static final String EVENT_GET_COMPLETED = "Get event request completed";

    public static final String EVENT_UPDATE_REQUEST = "Received request to update event";
    public static final String EVENT_UPDATE_COMPLETED = "Update event request completed";

    public static final String EVENT_DELETE_REQUEST = "Received request to delete event";
    public static final String EVENT_DELETE_COMPLETED = "Delete event request completed";
}
