package com.korur.oba.connect.outbox.entity;

// 1. Framework-level
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.time.Instant;

import com.korur.oba.connect.outbox.enums.OutboxEventType;
import com.korur.oba.connect.outbox.enums.OutboxStatus;
import com.korur.oba.connect.outbox.enums.ConnectOutboxSystem;
// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Outbox event entity used for reliable, transactional publishing of domain changes.
 * Each row represents one state change that must be delivered to an external system.
 */
@Table(name = "outbox_events", schema = "shrine")
@Entity
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OutboxEvent extends BaseEntity {

    /**
     * JSON payload describing the event in a self-contained way.
     * Stored as TEXT because payloads may grow beyond varchar limits.
     */
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    /**
     * Number of delivery attempts made for this event.
     */
    @Builder.Default
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    /**
     * Timestamp for backoff scheduling when the event has failed.
     */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /**
     * Timestamp when the event was successfully processed.
     */
    @Column(name = "processed_at")
    private Instant processedAt;

    /**
     * Last error message if event handling failed.
     */
    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    /**
     * Last error values if event handling failed.
     */
    @Column(name = "last_error_values", columnDefinition = "TEXT")
    private String lastErrorValues;

    /**
     * Last error stack trace if event handling failed.
     */
    @Column(name = "last_error_stack_trace", columnDefinition = "TEXT")
    private String lastErrorStacktrace;

    // -------- Enums --------

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "event_type", nullable = false)
    private OutboxEventType outboxEventType;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Builder.Default
    @Column(name = "status", nullable = false)
    private OutboxStatus outboxStatus = OutboxStatus.ENQUEUED;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "target_system", nullable = false, length = 30)
    private ConnectOutboxSystem connectOutboxSystem;

    // -------- Optimistic Locking --------

    /**
     * Optimistic locking column.
     * Prevents two workers from processing the same outbox event concurrently.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
