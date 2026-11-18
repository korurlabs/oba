package com.korur.oba.connect.retry.enums;

// 2. Third-party
import lombok.Getter;

// 4. Internal
import com.korur.oba.dojo.exception.external.InternalProcessException;

/**
 * Retry policy configuration per external system.
 *
 * <p>Each policy defines max attempts, initial delay, backoff multiplier,
 * and exception classification for synchronous retry via {@code @ConnectRetry}.</p>
 */
@Getter
public enum ConnectRetryPolicy {

    /**
     * Azure Blob Storage: 3 attempts, 1s initial delay, 2x backoff (~7s total).
     * Fast recovery for network/throttling failures.
     */
    AZURE_BLOB_STORAGE(
            ConnectRetrySystem.AZURE_BLOB_STORAGE,
            3, 1000L, 2.0
    ),

    /**
     * Intelligence/AI providers: 2 attempts, 3s initial delay, 2x backoff (~9s total).
     * AI calls are expensive; do not spam providers.
     */
    INTELLIGENCE(
            ConnectRetrySystem.INTELLIGENCE,
            2, 3000L, 2.0
    ),

    /**
     * Kubernetes cluster: 3 attempts, 2s initial delay, 2x backoff (~14s total).
     * Replaces manual Thread.sleep retry loops.
     */
    KUBERNETES(
            ConnectRetrySystem.KUBERNETES,
            3, 2000L, 2.0
    ),

    /**
     * Notification delivery: 3 attempts, 2s initial delay, 2x backoff (~14s total).
     * Email/notification services can experience transient failures.
     */
    NOTIFICATION(
            ConnectRetrySystem.NOTIFICATION,
            3, 2000L, 2.0
    ),

    /**
     * Expo Push Service: 3 attempts, 1s initial delay, 2x backoff (~3s of waiting).
     * A push is useful only within seconds of the message or incident, so the waits stay short.
     */
    EXPO_PUSH(
            ConnectRetrySystem.EXPO_PUSH,
            3, 1000L, 2.0
    );

    private final ConnectRetrySystem system;
    private final int maxAttempts;
    private final long delayMs;
    private final double multiplier;

    @SuppressWarnings("unchecked")
    private final Class<? extends Throwable>[] retryFor = new Class[]{ Exception.class };

    @SuppressWarnings("unchecked")
    private final Class<? extends Throwable>[] noRetryFor = new Class[]{ InternalProcessException.class };

    ConnectRetryPolicy(ConnectRetrySystem system, int maxAttempts, long delayMs, double multiplier) {
        this.system = system;
        this.maxAttempts = maxAttempts;
        this.delayMs = delayMs;
        this.multiplier = multiplier;
    }
}
