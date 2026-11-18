package com.korur.oba.apps.kaya.plan.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
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
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.plan.enums.PlanFeedbackDecision;

/**
 * One decision or comment of a reviewer on a {@link PlanVersion}, optionally anchored to a line range
 * of that version with a quote. What the reviewer gave is never changed; only the delivery fields
 * are set, once, when a session acknowledges the feedback. The plan, the version and the reviewer
 * (a user) are plain ids.
 */
@Entity
@Table(name = "plan_feedback", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PlanFeedback extends BaseEntity {

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "reviewer_id", nullable = false, updatable = false)
    private UUID reviewerId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "decision", nullable = false, updatable = false)
    private PlanFeedbackDecision decision;

    // Null only for an APPROVE.
    @Column(name = "comment", length = 4000, updatable = false)
    private String comment;

    // Both line bounds are set, or neither.
    @Column(name = "line_start", updatable = false)
    private Integer lineStart;

    @Column(name = "line_end", updatable = false)
    private Integer lineEnd;

    @Column(name = "quote", length = 500, updatable = false)
    private String quote;

    // Set together with deliveredSessionId when a session acknowledges the feedback.
    @Column(name = "delivered_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant deliveredAt;

    // The Claude Code session id text of the session that received the feedback.
    @Column(name = "delivered_session_id", length = 100)
    private String deliveredSessionId;
}
