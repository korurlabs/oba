package com.korur.oba.apps.kaya.session.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.session.enums.SessionDeliveredBy;
import com.korur.oba.apps.kaya.session.enums.SessionReleaseState;

/**
 * One Claude Code session of a user on one machine, as the wall lists it. The session id is
 * unique per organization. The organization, repository, user and machine are plain id columns,
 * so the session domain loads no entity of another domain. The account fields are copied when the
 * session starts, so a later account or seat change does not rewrite history.
 */
@Entity
@Table(
        name = "sessions",
        schema = "kaya",
        uniqueConstraints = @UniqueConstraint(
                name = "sessions_organization_id_session_id_uk",
                columnNames = {"organization_id", "session_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Session extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "machine_id", nullable = false)
    private UUID machineId;

    @Column(name = "session_id", length = 100, nullable = false, updatable = false)
    private String sessionId;

    // Empty while the session has neither a /rename title nor a first prompt.
    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "session_title", length = 200)
    private String sessionTitle;

    @Column(name = "first_prompt", length = 500)
    private String firstPrompt;

    @Column(name = "branch", length = 255)
    private String branch;

    @Column(name = "cwd", length = 500)
    private String cwd;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "claude_code_version", length = 40)
    private String claudeCodeVersion;

    @Column(name = "account_email", length = 320)
    private String accountEmail;

    @Column(name = "account_organization_uuid", length = 36)
    private String accountOrganizationUuid;

    @Column(name = "account_organization_name", length = 200)
    private String accountOrganizationName;

    @Column(name = "billing_type", length = 60)
    private String billingType;

    @Column(name = "seat_tier", length = 60)
    private String seatTier;

    @Column(name = "git_author_email", length = 320)
    private String gitAuthorEmail;

    // Set when the batch reported a git email that another user holds.
    @Column(name = "identity_conflict_user_id")
    private UUID identityConflictUserId;

    @Column(name = "started_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    private Instant startedAt;

    @Column(name = "last_event_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    private Instant lastEventAt;

    @Builder.Default
    @Column(name = "ended", nullable = false)
    private boolean ended = false;

    @Builder.Default
    @Column(name = "event_count", nullable = false)
    private int eventCount = 0;

    // Handoff directories the session wrote, as the recorder reports them.
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb", nullable = false)
    private List<String> features = new ArrayList<>();

    // The repository release the session ran under; null when the CLI reported none or Kaya could
    // not match the reported release. A plain id column, so the session domain loads no release entity.
    @Column(name = "release_id")
    private UUID releaseId;

    // Null until the recorder reports a release.
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "release_state")
    private SessionReleaseState releaseState;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "delivered_by")
    private SessionDeliveredBy deliveredBy;
}
