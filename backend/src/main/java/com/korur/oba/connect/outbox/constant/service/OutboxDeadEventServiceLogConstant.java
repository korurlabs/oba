package com.korur.oba.connect.outbox.constant.service;

/**
 * Log message constants for outbox dead event service layer.
 */
public final class OutboxDeadEventServiceLogConstant {

    private OutboxDeadEventServiceLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String DEAD_LETTER_MOVING = "Moving outbox event ID: {} to dead letter table";
    public static final String DEAD_LETTER_MOVED = "Outbox event ID: {} moved to dead letter with dead event ID: {}";
    public static final String DEAD_LETTER_MOVE_FAILED = "Failed to move outbox event ID: {} to dead letter table";
}
