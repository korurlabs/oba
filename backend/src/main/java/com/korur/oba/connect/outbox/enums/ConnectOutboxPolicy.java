package com.korur.oba.connect.outbox.enums;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Getter;

// 3. Java SDK
import java.util.Arrays;

/**
 * Outbox retry policy configuration per external system.
 *
 * <p>Each policy defines max attempts and a delay schedule (in minutes)
 * for asynchronous retry via the transactional outbox pattern.</p>
 */
@Getter
@AllArgsConstructor
public enum ConnectOutboxPolicy {

    /**
     * Email/notification service.
     * 3 retries over ~100 minutes: notifications become stale quickly.
     */
    NOTIFICATION(
            ConnectOutboxSystem.NOTIFICATION,
            3, new int[]{ 10, 30, 60 }
    ),

    /**
     * Azure Blob Storage operations (upload, delete).
     * 4 retries over ~6 hours: transient network/throttling failures.
     */
    AZURE_BLOB_STORAGE(
            ConnectOutboxSystem.AZURE_BLOB_STORAGE,
            4, new int[]{ 5, 15, 60, 240 }
    ),

    /**
     * Kubernetes cluster operations (command execution, deployments).
     * 5 retries over ~24 hours: cluster recovery time.
     */
    KUBERNETES(
            ConnectOutboxSystem.KUBERNETES,
            5, new int[]{ 5, 15, 60, 240, 720 }
    ),

    /**
     * Intelligence/AI provider operations (chat, multimodal).
     * 3 retries over ~2 hours: rate limits clear quickly.
     */
    INTELLIGENCE(
            ConnectOutboxSystem.INTELLIGENCE,
            3, new int[]{ 5, 30, 60 }
    );

    private final ConnectOutboxSystem system;
    private final int maxAttempts;
    private final int[] delaysMinutes;

    /**
     * Finds the policy for the given outbox system.
     *
     * @param system the connect outbox system
     * @return the matching policy
     * @throws IllegalArgumentException if no policy exists for the system
     */
    public static ConnectOutboxPolicy findBySystem(ConnectOutboxSystem system) {
        return Arrays.stream(values())
                .filter(policy -> policy.system == system)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No ConnectOutboxPolicy found for system: " + system));
    }
}
