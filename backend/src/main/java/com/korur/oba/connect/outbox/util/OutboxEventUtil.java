package com.korur.oba.connect.outbox.util;

// 2. Third-party
import org.apache.commons.lang3.exception.ExceptionUtils;

// 3. Java SDK
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Random;

// 4. Internal
import com.korur.oba.connect.outbox.entity.OutboxDeadEvent;
import com.korur.oba.connect.outbox.entity.OutboxEvent;
import com.korur.oba.connect.outbox.enums.ConnectOutboxPolicy;
import com.korur.oba.connect.outbox.enums.OutboxStatus;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.util.JsonUtil;


/**
 * Utility methods for managing outbox event state transitions.
 */
public final class OutboxEventUtil {

    private OutboxEventUtil() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    private static final Random RANDOM = new SecureRandom();

    /**
     * Marks the given event as processed.
     */
    public static void markAsProcessed(OutboxEvent event) {
        event.setOutboxStatus(OutboxStatus.PROCESSED);
        event.setProcessedAt(Instant.now());
        event.setNextAttemptAt(null);
    }

    /**
     * Marks the given event as failed or re-enqueued if retries remain.
     * Policy provides maxAttempts and delay schedule.
     */
    public static void markAsFailed(OutboxEvent event, Exception ex, ConnectOutboxPolicy policy) {
        event.setLastErrorMessage(ex.getMessage());
        event.setLastErrorStacktrace(ExceptionUtils.getStackTrace(ex));

        if (ex instanceof ApplicationException applicationException) {
            Map<String, Object> errorValues = applicationException.getErrorValues();
            if (errorValues != null && !errorValues.isEmpty()) {
                event.setLastErrorValues(JsonUtil.toJson(errorValues));
            }
        }

        if (event.getAttempts() >= policy.getMaxAttempts()) {
            event.setOutboxStatus(OutboxStatus.FAILED);
            event.setNextAttemptAt(null);
        } else {
            event.setNextAttemptAt(calculateNextAttempt(
                    event.getAttempts(), policy.getDelaysMinutes()));
            event.setOutboxStatus(OutboxStatus.ENQUEUED);
        }
    }

    /**
     * Marks the given event as failed immediately without checking retries.
     * Used for internal errors that should not be retried.
     */
    public static void markAsFailedImmediately(OutboxEvent event, Exception ex) {
        event.setLastErrorMessage(ex.getMessage());
        event.setLastErrorStacktrace(ExceptionUtils.getStackTrace(ex));

        if (ex instanceof ApplicationException applicationException) {
            Map<String, Object> errorValues = applicationException.getErrorValues();
            if (errorValues != null && !errorValues.isEmpty()) {
                event.setLastErrorValues(JsonUtil.toJson(errorValues));
            }
        }

        event.setOutboxStatus(OutboxStatus.FAILED);
        event.setNextAttemptAt(null);
    }

    /**
     * Maps an outbox event to a dead letter record, preserving all relevant fields.
     *
     * @param event the outbox event that permanently failed
     * @return a new {@code OutboxDeadEvent} ready to be persisted
     */
    public static OutboxDeadEvent toDeadEvent(OutboxEvent event) {
        return OutboxDeadEvent.builder()
                .originalEventId(event.getId())
                .payload(event.getPayload())
                .outboxEventType(event.getOutboxEventType())
                .connectOutboxSystem(event.getConnectOutboxSystem())
                .attempts(event.getAttempts())
                .lastErrorMessage(event.getLastErrorMessage())
                .lastErrorValues(event.getLastErrorValues())
                .lastErrorStacktrace(event.getLastErrorStacktrace())
                .failedAt(Instant.now())
                .originalCreatedAt(event.getCreatedAt())
                .build();
    }

    /**
     * Calculates when the next retry should occur after a failed outbox event.
     *
     * @param attempts      current value of the attempts counter (already incremented)
     * @param delaysMinutes the delay schedule in minutes for the target system
     * @return the Instant when this event should be retried, null if attempts < 1
     */
    public static Instant calculateNextAttempt(int attempts, int[] delaysMinutes) {
        if (attempts < 1) {
            return null;
        }

        int index = Math.min(attempts - 1, delaysMinutes.length - 1);
        int baseMinutes = delaysMinutes[index];

        // ±20% jitter to prevent thundering herd
        int jitterMinutes = (int) (baseMinutes * 0.2 * (RANDOM.nextDouble() * 2 - 1));
        int finalMinutes = Math.max(1, baseMinutes + jitterMinutes);

        return Instant.now().plus(Duration.ofMinutes(finalMinutes));
    }
}
