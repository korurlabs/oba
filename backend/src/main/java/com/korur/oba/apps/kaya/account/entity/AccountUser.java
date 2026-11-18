package com.korur.oba.apps.kaya.account.entity;

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
import com.korur.oba.apps.kaya.account.enums.AccountUserSource;
import com.korur.oba.shrine.identity.entity.User;

/**
 * A user's use of an {@link Account}. An account is shared by every user linked to it, so
 * teammates on one subscription see its usage once, and a user can use several accounts; one link
 * exists per pair. lastSeenAt is this user's latest use, while {@link Account#getLastSeenAt()} is
 * the latest use by any user.
 */
@Entity
@Table(name = "account_users", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AccountUser extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    // Fetch: EAGER -- Hibernate 6 refuses a LAZY to-one whose target (User) uses @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "source", nullable = false, updatable = false)
    private AccountUserSource source;

    @Column(name = "last_seen_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    private Instant lastSeenAt;
}
