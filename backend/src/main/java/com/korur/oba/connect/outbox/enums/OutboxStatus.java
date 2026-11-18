package com.korur.oba.connect.outbox.enums;

/**
 * Processing state of an outbox event.
 *
 * ENQUEUED:
 *   Event is created in the same transaction as the domain change.
 *   Worker has not claimed it yet.
 *
 * PROCESSING:
 *   Worker has claimed the event and is attempting delivery.
 *
 * PROCESSED:
 *   Delivery succeeded and no further work is required.
 *
 * FAILED:
 *   Delivery failed permanently after max retries.
 *   Manual intervention may be required.
 */
public enum OutboxStatus {
    ENQUEUED,
    PROCESSING,
    PROCESSED,
    FAILED
}
