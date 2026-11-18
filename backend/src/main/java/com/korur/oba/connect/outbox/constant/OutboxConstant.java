package com.korur.oba.connect.outbox.constant;

/**
 * Field name constants for the outbox domain.
 */
public final class OutboxConstant {

    private OutboxConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String FIELD_OUTBOX_EVENT_ID = "outboxEventId";
    public static final String FIELD_TARGET_SYSTEM = "targetSystem";
}
