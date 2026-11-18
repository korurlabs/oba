package com.korur.oba.connect.outbox.constant.aspect;

/**
 * Log message constants for the ConnectOutboxAspect.
 */
public final class ConnectOutboxAspectLogConstant {

    private ConnectOutboxAspectLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String OUTBOX_EVENT_ENQUEUING = "Enqueuing outbox event for system={}, eventType={}";
    public static final String OUTBOX_EVENT_ENQUEUED = "Outbox event enqueued for system={}, eventType={}, eventId={}";
}
