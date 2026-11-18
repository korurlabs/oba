package com.korur.oba.apps.kaya.account.entity;

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
import com.korur.oba.apps.kaya.account.enums.AccountProvider;

/**
 * An account of an AI provider used within the organization, by any number of users.
 *
 * The provider, the account email and, for a team or enterprise subscription, the provider
 * organization UUID identify it within the organization; a personal account has no organization
 * UUID. These never change, so a different value is a different account. The other fields describe
 * the subscription as a recorder last reported it. The users using it are its
 * {@link AccountUser} links.
 */
@Entity
@Table(name = "accounts", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Account extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "provider", nullable = false, updatable = false)
    private AccountProvider provider;

    @Column(name = "account_email", length = 320, nullable = false, updatable = false)
    private String accountEmail;

    @Column(name = "account_organization_uuid", length = 36, updatable = false)
    private String accountOrganizationUuid;

    @Column(name = "account_organization_name", length = 200)
    private String accountOrganizationName;

    @Column(name = "billing_type", length = 60)
    private String billingType;

    @Column(name = "organization_type", length = 60)
    private String organizationType;

    @Column(name = "seat_tier", length = 60)
    private String seatTier;

    @Column(name = "last_seen_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    private Instant lastSeenAt;
}
