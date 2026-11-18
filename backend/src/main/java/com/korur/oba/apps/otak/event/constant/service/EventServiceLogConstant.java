package com.korur.oba.apps.otak.event.constant.service;

/**
 * Log message constants for event service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class EventServiceLogConstant {

    private EventServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String EVENT_CREATED = "Event created: id={}, organizationId={}, createdBy={}";
    public static final String EVENT_UPDATED = "Event updated: id={}, organizationId={}";
    public static final String EVENT_DELETED = "Event deleted: id={}, organizationId={}";
    public static final String LISTING_EVENTS = "Listing events: organizationId={}, request={}";
    public static final String READING_EVENT = "Reading event: id={}, organizationId={}";
}
