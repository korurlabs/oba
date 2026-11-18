package com.korur.oba.apps.kaya.session.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.session.enums.SessionSkillUseSource;

/**
 * How often a session used a skill, per way of use. Derived from the session's events on every batch.
 */
@Entity
@Table(
        name = "session_skill_uses",
        schema = "kaya",
        uniqueConstraints = @UniqueConstraint(
                name = "session_skill_uses_session_id_skill_source_uk", columnNames = {"session_id", "skill", "source"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SessionSkillUse extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private Session session;

    @Column(name = "skill", length = 100, nullable = false, updatable = false)
    private String skill;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "source", nullable = false, updatable = false)
    private SessionSkillUseSource source;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    @Column(name = "first_used_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    private Instant firstUsedAt;
}
