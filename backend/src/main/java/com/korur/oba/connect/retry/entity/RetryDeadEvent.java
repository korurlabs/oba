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

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.outbox.enums.DeadLetterResolution;
import com.korur.oba.connect.retry.enums.ConnectRetrySystem;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Dead letter record for retry executions that exhausted all attempts.
 *
 * <p>Created alongside a {@link RetryEvent} with status EXHAUSTED. Provides
 * a separate table for operational visibility into permanently failed
 * synchronous retries.</p>
 */
@Table(name = "retry_dead_events", schema = "shrine")
@Entity
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RetryDeadEvent extends BaseEntity {

    /**
     * ID of the associated retry event record.
     */
    @Column(name = "original_retry_event_id", nullable = false)
    private UUID originalRetryEventId;

    /**
     * Fully qualified method name that exhausted all retries.
     */
    @Column(name = "method_name", nullable = false)
    private String methodName;

    /**
     * The external system that was being retried.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "system", nullable = false)
    private ConnectRetrySystem connectRetrySystem;

    /**
     * Total attempts made before exhaustion.
     */
    @Column(name = "attempts", nullable = false)
    private int attempts;

    /**
     * Error message from the final failed attempt.
     */
    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    /**
     * Full stack trace from the final failed attempt.
     */
    @Column(name = "last_error_stack_trace", columnDefinition = "TEXT")
    private String lastErrorStacktrace;

    /**
     * Timestamp when this event was moved to the dead letter table.
     */
    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    /**
     * Resolution action taken on this dead letter event, if any.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "resolution")
    private DeadLetterResolution resolution;

    /**
     * Timestamp when this dead letter event was resolved.
     */
    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
