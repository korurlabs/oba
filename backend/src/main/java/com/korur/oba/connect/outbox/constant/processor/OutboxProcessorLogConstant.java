package com.korur.oba.connect.outbox.constant.processor;

/**
 * Log message constants for outbox processor layer.
 */
public final class OutboxProcessorLogConstant {

    private OutboxProcessorLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String OUTBOX_PROCESSING_START = "Starting processing event ID: {}";
    public static final String OUTBOX_PROCESSING_ALREADY_CLAIMED = "Event ID: {} already claimed by another worker";
    public static final String OUTBOX_PROCESSING_SUCCESS = "Event ID: {} processed successfully";
    public static final String OUTBOX_PROCESSING_FAILED = "Failed to process event ID: {}";
    public static final String OUTBOX_PROCESSING_THIRD_PARTY_ERROR = "Event ID: {} processed by third party but unexpected error occurred";
    public static final String OUTBOX_PROCESSING_RE_ENQUEUED = "Event ID: {} re-enqueued for retry. Attempt {}/{} for {} [{}]. Next retry at: {}. Payload: {}";
    public static final String OUTBOX_PROCESSING_PERMANENTLY_FAILED = "Event ID: {} permanently FAILED after {}/{} attempts for {} [{}]. Payload: {}";
    public static final String OUTBOX_PROCESSING_SAVE_FAILED = "CRITICAL: Failed to persist failure state for event ID: {}";
    public static final String OUTBOX_PROCESSING_MOVED_TO_DEAD_LETTER = "Event ID: {} moved to dead letter table";
}
