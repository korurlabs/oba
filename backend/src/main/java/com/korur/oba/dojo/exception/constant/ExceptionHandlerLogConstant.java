package com.korur.oba.dojo.exception.constant;

/**
 * Log message constants for the {@code GlobalExceptionHandler}.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ExceptionHandlerLogConstant {

    private ExceptionHandlerLogConstant() {
    }

    // Error log messages
    public static final String LOG_APPLICATION_ERROR = "Application error: {}";
    public static final String LOG_UNEXPECTED_ERROR = "Unexpected error: {}";
    public static final String LOG_UNHANDLED_SYNC_ERROR = "Unhandled sync error: {}";
    public static final String LOG_DATABASE_TIMEOUT = "Database work ended at the time limit of its transaction: {}";

    // Warn log messages
    public static final String LOG_CONCURRENT_UPDATE = "Concurrent update rejected by optimistic locking: {}";
    public static final String LOG_ASYNC_REQUEST_TIMED_OUT = "Async request timed out: {}";
    public static final String LOG_CLIENT_GONE = "Client went away before the response was written: {}";
}
