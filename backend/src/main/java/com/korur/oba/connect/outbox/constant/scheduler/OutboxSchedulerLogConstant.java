package com.korur.oba.connect.outbox.constant.scheduler;

/**
 * Log message constants for outbox scheduler layer.
 */
public final class OutboxSchedulerLogConstant {

    private OutboxSchedulerLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String OUTBOX_PROCESSING_BATCH = "Starting processing of {} outbox events";
}
