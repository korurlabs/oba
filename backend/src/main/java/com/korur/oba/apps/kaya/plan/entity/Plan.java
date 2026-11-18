package com.korur.oba.apps.kaya.plan.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
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
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.plan.enums.PlanKind;
import com.korur.oba.apps.kaya.plan.enums.PlanReviewState;

/**
 * A plan Claude Code wrote: a {@link PlanKind#SPEC} file of a repository, found by its repository and
 * path, or the {@link PlanKind#SESSION} plan of one session. The kind, path and session are its
 * identity and never change. The organization, repository, sessions, user and pack are
 * plain id columns, so the plan domain loads no entity of another domain.
 *
 * The title, the latest version fields, the user and the review state describe the latest
 * version; they and the open feedback count are kept in the transaction that stores a version or
 * a feedback, so the plans list reads them without joining versions or feedback.
 */
@Entity
@Table(name = "plans", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Plan extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "repository_id", nullable = false, updatable = false)
    private UUID repositoryId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "kind", nullable = false, updatable = false)
    private PlanKind kind;

    // Repository-relative path of a SPEC plan; null for a SESSION plan.
    @Column(name = "path", length = 500, updatable = false)
    private String path;

    // The session a SESSION plan belongs to; null for a SPEC plan.
    @Column(name = "session_id", updatable = false)
    private UUID sessionId;

    @Column(name = "title", length = 300, nullable = false)
    private String title;

    // The pack the spec is about; null when none matches. Cleared when the pack is deleted.
    @Column(name = "pack_id")
    private UUID packId;

    // The handoffs/{feature}/ directory whose planning handoff names this plan.
    @Column(name = "feature", length = 100)
    private String feature;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "review_state", nullable = false)
    private PlanReviewState reviewState = PlanReviewState.OPEN;

    @Builder.Default
    @Column(name = "latest_version_number", nullable = false)
    private int latestVersionNumber = 0;

    // The session that wrote the latest version; cleared when that session is deleted.
    @Column(name = "latest_session_id")
    private UUID latestSessionId;

    // The user of the session that wrote the latest version.
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Feedback rows of this plan not yet delivered to a session.
    @Builder.Default
    @Column(name = "open_feedback_count", nullable = false)
    private int openFeedbackCount = 0;

    @Column(name = "last_changed_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    private Instant lastChangedAt;
}
