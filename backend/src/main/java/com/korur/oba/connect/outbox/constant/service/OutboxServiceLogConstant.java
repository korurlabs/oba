package com.korur.oba.connect.outbox.constant.service;

/**
 * Log message constants for outbox service layer.
 */
public final class OutboxServiceLogConstant {

    private OutboxServiceLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String OUTBOX_EVENT_CREATING = "Creating outbox event for eventType: {}, targetSystem: {}";
    public static final String OUTBOX_EVENT_CREATED = "Created outbox event with ID: {}";
    public static final String OUTBOX_EVENT_BATCH_CREATED = "Created {} outbox events to process";
    public static final String OUTBOX_EVENT_CLAIMING = "Claiming outbox event with ID: {}";
    public static final String OUTBOX_EVENT_ALREADY_CLAIMED = "Outbox event already claimed with ID: {}";
    public static final String OUTBOX_EVENT_CLAIMED = "Outbox event claimed with ID: {}";
}
