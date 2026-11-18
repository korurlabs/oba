package com.korur.oba.apps.otak.event.constant;

/**
 * Field names, limits and validation messages of the event domain. The limits repeat the
 * column sizes and check constraint of otak.events, so a request that passes validation
 * always fits the table.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class EventConstant {

    private EventConstant() {
        // Private constructor to prevent instantiation
    }

    // -------------Field names-----------//

    public static final String FIELD_ID = "id";
    public static final String FIELD_EVENT_ID = "eventId";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_START_DATE = "startDate";
    public static final String FIELD_END_DATE = "endDate";
    public static final String FIELD_PERIOD = "period";

    // -------------Limits-----------//

    public static final int NAME_MAX_LENGTH = 200;
    public static final int CITY_MAX_LENGTH = 100;
    public static final int WEBSITE_MAX_LENGTH = 500;
    public static final int NOTES_MAX_LENGTH = 10000;
    public static final int SEARCH_MAX_LENGTH = 200;

    // -------------Validation messages-----------//

    public static final String NAME_REQUIRED_MESSAGE = "Name is required";
    public static final String NAME_SIZE_MESSAGE = "Name can not be longer than 200 characters";
    public static final String TYPE_REQUIRED_MESSAGE = "Type is required";
    public static final String START_DATE_REQUIRED_MESSAGE = "Start date is required";
    public static final String CITY_BLANK_MESSAGE = "City can not be empty; send null when unknown";
    public static final String CITY_SIZE_MESSAGE = "City can not be longer than 100 characters";
    public static final String WEBSITE_BLANK_MESSAGE = "Website can not be empty; send null when unknown";
    public static final String WEBSITE_SIZE_MESSAGE = "Website can not be longer than 500 characters";
    public static final String WEBSITE_PATTERN_MESSAGE = "Website must be a full http or https address";
    public static final String NOTES_BLANK_MESSAGE = "Notes can not be empty; send null for none";
    public static final String NOTES_SIZE_MESSAGE = "Notes can not be longer than 10000 characters";
    public static final String SEARCH_BLANK_MESSAGE = "Search can not be empty; leave it out to list every event";
    public static final String SEARCH_SIZE_MESSAGE = "Search can not be longer than 200 characters";
}
