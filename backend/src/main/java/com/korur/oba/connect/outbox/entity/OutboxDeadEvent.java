package com.korur.oba.connect.outbox.entity;

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
import com.korur.oba.connect.outbox.enums.ConnectOutboxSystem;
import com.korur.oba.connect.outbox.enums.DeadLetterResolution;
import com.korur.oba.connect.outbox.enums.OutboxEventType;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Dead letter record for outbox events that permanently failed after exhausting all retries.
 *
 * <p>When an outbox event reaches max attempts or fails immediately due to an internal error,
 * it is moved from the active {@code outbox_events} table to this dead letter table.
 * This keeps the active table clean and provides a separate lifecycle for failed events.</p>
 */
@Table(name = "outbox_dead_events", schema = "shrine")
@Entity
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OutboxDeadEvent extends BaseEntity {

    /**
     * ID of the original outbox event before it was moved to the dead letter table.
     */
    @Column(name = "original_event_id", nullable = false)
    private UUID originalEventId;

    /**
     * JSON payload from the original outbox event.
     */
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    /**
     * The type of event that failed.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "event_type", nullable = false)
    private OutboxEventType outboxEventType;

    /**
     * The external system this event was targeting.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "target_system", nullable = false, length = 30)
    private ConnectOutboxSystem connectOutboxSystem;

    /**
     * Total delivery attempts made before the event was moved to dead letter.
     */
    @Column(name = "attempts", nullable = false)
    private int attempts;

    /**
     * Error message from the final failed attempt.
     */
    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    /**
     * JSON error context values from the final failed attempt.
     */
    @Column(name = "last_error_values", columnDefinition = "TEXT")
    private String lastErrorValues;

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

    /**
     * Timestamp when the original outbox event was created.
     */
    @Column(name = "original_created_at", nullable = false)
    private Instant originalCreatedAt;
}
