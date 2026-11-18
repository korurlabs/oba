package com.korur.oba.apps.kaya.machine.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.shrine.identity.entity.User;

/**
 * A recorder token of a machine. Only the SHA-256 hash of the token is stored, so nobody can read
 * a token after it was issued; the prefix lets a user tell tokens apart. A machine has at most one
 * token whose revokedAt is null (a partial unique index), so issuing a new one revokes the old one
 * first. The user is always the machine's user.
 */
@Entity
@Table(name = "machine_tokens", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MachineToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machine_id", nullable = false, updatable = false)
    private Machine machine;

    // Fetch: EAGER -- Hibernate 6 refuses a LAZY to-one whose target uses @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_prefix", length = 12, nullable = false, updatable = false)
    private String tokenPrefix;

    @Column(name = "token_hash", length = 64, nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "last_used_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant lastUsedAt;

    @Column(name = "expires_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant revokedAt;
}
