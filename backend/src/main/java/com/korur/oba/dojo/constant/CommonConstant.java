package com.korur.oba.dojo.constant;

/**
 * Common constants used across the Village application.
 */
public final class CommonConstant {

    private CommonConstant() {
        throw new UnsupportedOperationException(UTILITY_CLASS_MESSAGE);
    }

    public static final String UTILITY_CLASS_MESSAGE = "Utility class. Cannot be instantiated.";

    public static final String ID = "id";

    public static final String SIZE_MESSAGE_255 = "Length field size can not be more then 255";
    public static final String SIZE_MESSAGE_254 = "Length field size can not be more then 254";
    public static final String SIZE_MESSAGE_34 = "Length field size can not be more then 34";
    public static final String SIZE_MESSAGE_30 = "Length field size can not be more then 30";
    public static final String SIZE_MESSAGE_25 = "Length field size can not be more then 25";
    public static final String SIZE_MESSAGE_14 = "Length field size can not be more then 14";
    public static final String SIZE_MESSAGE_MIN_6 = "Length field size must be more then 6";

    public static final String NOT_NULL_MESSAGE = "Field can not be null";
    public static final String NOT_BLANK_MESSAGE = "Field can not be blank, meaning it can not be null and can not have whitespaces";
    public static final String NOT_EMPTY_MESSAGE = "Field can not be empty, meaning it can not be null and can not have whitespaces";
    public static final String NOT_VALID_EMAIL_MESSAGE = "A valid email address must be provided. It must guarentee the following conditions..";
    public static final String PASSWORD_LENGTH_MESSAGE = "Password must be between 12 and 128 characters long.";
    public static final String NOT_VALID_UUID = "Field must be a valid UUID";

    // Header name used to pass the organization context for org-scoped requests.
    public static final String HEADER_ORGANIZATION_ID = "X-Organization-ID";

    // Observe-level bookend log constants — standard start/end per layer.
    // Controller layer
    public static final String LOG_RECEIVED_REQUEST = "Received request";
    public static final String LOG_COMPLETED_REQUEST = "Completed request";

    // Service layer
    public static final String LOG_PROCESSING = "Processing";
    public static final String LOG_COMPLETED_PROCESSING = "Completed processing";

    // Helper / utility layer
    public static final String LOG_EXECUTING = "Executing";
    public static final String LOG_COMPLETED_EXECUTION = "Completed execution";

    public static final String PAGE_MIN_MESSAGE = "Page must be at least 1";
    public static final String AMOUNT_MIN_MESSAGE = "amount must be at least 1";
    public static final String AMOUNT_MAX_MESSAGE = "amount cannot exceed 50";
    public static final String SORT_DIRECTION_MESSAGE = "Sort direction must be 'asc' or 'desc'";
}
