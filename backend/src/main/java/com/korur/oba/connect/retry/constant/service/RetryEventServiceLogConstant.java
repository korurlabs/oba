package com.korur.oba.connect.retry.constant.service;

/**
 * Log message constants for retry event service layer.
 */
public final class RetryEventServiceLogConstant {

    private RetryEventServiceLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String RETRY_EVENT_CREATING_COMPLETED = "Creating completed retry event for method: {}, system: {}, attempts: {}";
    public static final String RETRY_EVENT_CREATED_COMPLETED = "Created completed retry event with ID: {}";
    public static final String RETRY_EVENT_CREATING_EXHAUSTED = "Creating exhausted retry event for method: {}, system: {}, attempts: {}";
    public static final String RETRY_EVENT_CREATED_EXHAUSTED = "Created exhausted retry event with ID: {} and dead letter ID: {}";
    public static final String RETRY_EVENT_PERSIST_FAILED = "Failed to persist retry event for method: {}, system: {}";
}
