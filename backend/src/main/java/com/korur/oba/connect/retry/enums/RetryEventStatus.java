package com.korur.oba.connect.retry.enums;

/**
 * Status of a persisted retry event.
 *
 * <ul>
 *   <li>COMPLETED — Operation succeeded after one or more retries.</li>
 *   <li>EXHAUSTED — All retry attempts were exhausted without success.</li>
 * </ul>
 */
public enum RetryEventStatus {
    COMPLETED,
    EXHAUSTED
}
