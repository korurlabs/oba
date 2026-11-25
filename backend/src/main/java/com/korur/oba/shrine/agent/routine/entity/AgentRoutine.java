package com.korur.oba.shrine.agent.routine.entity;

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
import com.korur.oba.connect.routine.core.enums.RoutineFireStatus;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * The hosted routine Oba fires for a ROUTINE agent, with the secret that fires it. One per agent.
 *
 * The vendor is not stored: it is the agent's provider. Organization and agent are plain ids, not
 * relations, so reading a routine joins nothing. The secret is kept only encrypted; the prefix is the
 * short visible start shown to an ADMIN so they can tell which secret is saved.
 */
@Entity
@Table(name = "agent_routines", schema = "shrine")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AgentRoutine extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "routine_id", length = 128, nullable = false)
    private String routineId;

    @Column(name = "routine_secret_encrypted", columnDefinition = "TEXT", nullable = false)
    private String routineSecretEncrypted;

    @Column(name = "routine_secret_prefix", length = 16, nullable = false)
    private String routineSecretPrefix;

    @Column(name = "last_fired_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant lastFiredAt;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "last_fire_status")
    private RoutineFireStatus lastFireStatus;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "last_session_url", columnDefinition = "TEXT")
    private String lastSessionUrl;
}
