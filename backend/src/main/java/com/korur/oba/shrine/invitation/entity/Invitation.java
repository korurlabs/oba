package com.korur.oba.shrine.invitation.entity;

// 1. Framework-level
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.invitation.enums.InvitationStatus;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Tracks invitations for users to join an organization.
 * All invitations start as PENDING and require explicit user decision
 * (accept or decline) via the decision endpoint.
 */
@Entity
@Table(name = "user_invitations", schema = "shrine")
@Getter
@Setter
@SuperBuilder
@RequiredArgsConstructor
@AllArgsConstructor
public class Invitation extends BaseEntity {

    // Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "role", nullable = false)
    private OrganizationMemberRole role;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    @lombok.Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    // Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    private User invitedBy;

    @Column(name = "accepted_at", nullable = true)
    private Instant acceptedAt;

    @Column(name = "expires_at", nullable = true)
    private Instant expiresAt;

    @Column(name = "declined_at", nullable = true)
    private Instant declinedAt;
}
