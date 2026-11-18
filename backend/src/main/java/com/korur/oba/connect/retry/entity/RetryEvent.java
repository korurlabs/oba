package com.korur.oba.connect.retry.entity;

// 1. Framework-level
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 4. Internal
import com.korur.oba.connect.retry.enums.ConnectRetrySystem;
import com.korur.oba.connect.retry.enums.RetryEventStatus;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Persisted record of a synchronous retry execution via {@code @ConnectRetry}.
 *
 * <p>Created only when retries were needed (attempt count > 1 on success)
 * or when all attempts were exhausted. First-try successes are not persisted
 * to avoid noise.</p>
 */
@Table(name = "retry_events", schema = "shrine")
@Entity
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RetryEvent extends BaseEntity {

    /**
     * Fully qualified method name that was retried.
     */
    @Column(name = "method_name", nullable = false)
    private String methodName;

    /**
     * The external system being retried.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "system", nullable = false)
    private ConnectRetrySystem connectRetrySystem;

    /**
     * Total attempts made (including the initial attempt).
     */
    @Column(name = "attempts", nullable = false)
    private int attempts;

    /**
     * Maximum attempts allowed by the policy.
     */
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    /**
     * Outcome of the retry execution.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    private RetryEventStatus status;

    /**
     * Error message from the final attempt (populated when exhausted).
     */
    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    /**
     * Stack trace from the final attempt (populated when exhausted).
     */
    @Column(name = "last_error_stack_trace", columnDefinition = "TEXT")
    private String lastErrorStacktrace;

    /**
     * Total wall-clock time in milliseconds spent retrying.
     */
    @Column(name = "duration_ms", nullable = false)
    private long durationMs;
}
