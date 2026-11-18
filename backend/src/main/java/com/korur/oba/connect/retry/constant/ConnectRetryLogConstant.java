package com.korur.oba.connect.retry.constant;

/**
 * Log message constants for connect retry operations.
 */
public final class ConnectRetryLogConstant {

    private ConnectRetryLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String RETRY_ATTEMPT = "Retry attempt {}/{} for system: {}";
    public static final String RETRY_EXHAUSTED = "All retry attempts exhausted for system: {} after {} attempts";
    public static final String RETRY_SUCCESS_AFTER_RETRIES = "Operation succeeded for system: {} after {} retries";
    public static final String RETRY_EVENT_PERSIST_FAILED = "Failed to persist retry event for method: {}, system: {}";
}
