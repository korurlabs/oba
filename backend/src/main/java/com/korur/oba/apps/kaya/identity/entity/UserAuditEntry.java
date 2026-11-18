package com.korur.oba.apps.kaya.identity.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

// 3. Java SDK
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.identity.enums.UserAuditAction;
import com.korur.oba.apps.kaya.identity.enums.UserAuditActorKind;

/**
 * One entry of the append-only audit log of a user in an organization: who did what, when, why,
 * and the values involved. Entries are only ever inserted, through
 * {@link com.korur.oba.apps.kaya.identity.service.UserAuditRecorder}, in the transaction of the
 * action they describe; Hibernate treats the entity as immutable and every column is excluded
 * from updates, so a stray change can never rewrite history.
 */
@Entity
@Immutable
@Table(name = "user_audit_entries", schema = "kaya")
@AttributeOverride(name = "updatedAt",
        column = @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE",
                nullable = false, updatable = false))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserAuditEntry extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    // Fetch: EAGER -- Hibernate 6 refuses a LAZY to-one whose target (User) uses @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "actor_kind", nullable = false, updatable = false)
    private UserAuditActorKind actorKind;

    // The acting user for USER entries; null for SYSTEM and RECORDER, and after the user is deleted.
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "action", nullable = false, updatable = false)
    private UserAuditAction action;

    @Column(name = "reason", length = 1000, updatable = false)
    private String reason;

    // Counts, ids and values before and after; always a JSON object.
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb", nullable = false, updatable = false)
    private Map<String, Object> details = new HashMap<>();
}
