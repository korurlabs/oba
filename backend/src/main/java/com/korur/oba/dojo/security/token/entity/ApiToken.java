package com.korur.oba.dojo.security.token.entity;

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
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;

/**
 * A bearer token that a machine or an agent presents instead of a Keycloak JWT.
 *
 * Only the SHA-256 hash of the token is stored, so nobody can read a token after it was issued; the
 * prefix lets a person tell tokens apart. A subject has at most one token of a kind whose revokedAt is
 * null (a partial unique index), so issuing a new one revokes the old one first.
 *
 * The subject is the machine or the agent user the token stands for and has no foreign key, because
 * it lives in a different table per kind. The owner and the organization are UUID columns rather than
 * relations, so authenticating a request loads one row and no join.
 */
@Entity
@Table(name = "api_tokens", schema = "dojo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ApiToken extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "kind", nullable = false, updatable = false)
    private ApiTokenKind kind;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "token_prefix", length = 12, nullable = false, updatable = false)
    private String tokenPrefix;

    @Column(name = "token_hash", length = 64, nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant lastUsedAt;

    @Column(name = "revoked_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant revokedAt;
}
