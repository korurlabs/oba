package com.korur.oba.connect.retry.service;

// 4. Internal
import com.korur.oba.connect.retry.enums.ConnectRetryPolicy;

/**
 * Write operations for retry event persistence.
 */
public interface RetryEventService {

    /**
     * Creates a retry event record for a successful operation that required retries.
     *
     * @param methodName fully qualified method name
     * @param policy     the retry policy that was applied
     * @param attempts   total attempts made
     * @param durationMs total wall-clock time spent retrying
     */
    void createCompleted(String methodName, ConnectRetryPolicy policy, int attempts, long durationMs);

    /**
     * Creates a retry event record and a corresponding dead letter record
     * for an operation that exhausted all retry attempts.
     *
     * @param methodName fully qualified method name
     * @param policy     the retry policy that was applied
     * @param attempts   total attempts made
     * @param durationMs total wall-clock time spent retrying
     * @param ex         the exception from the final attempt
     */
    void createExhaustedWithDeadLetter(String methodName, ConnectRetryPolicy policy,
                                       int attempts, long durationMs, Exception ex);
}
