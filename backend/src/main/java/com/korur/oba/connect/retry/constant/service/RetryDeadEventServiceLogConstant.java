package com.korur.oba.connect.retry.constant.service;

/**
 * Log message constants for retry dead event service layer.
 */
public final class RetryDeadEventServiceLogConstant {

    private RetryDeadEventServiceLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String DEAD_LETTER_CREATING = "Creating retry dead letter for method: {}, system: {}";
    public static final String DEAD_LETTER_CREATED = "Created retry dead letter with ID: {} for retry event ID: {}";
}
