package com.korur.oba.shrine.access.entity;

// 1. Framework-level

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;

/**
 * An organization's grant of one app to one user or to one group.
 *
 * Exactly one of user and group is set; the database refuses a row with none or both. Among the rows that
 * are not deleted, an organization grants an app to a user once and to a group once, so a revoked grant can
 * be made again.
 */
@Entity
@Table(name = "organization_app_grants", schema = "shrine")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class AppGrant extends SoftDeleteBaseEntity {

    // Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "app", nullable = false)
    private App app;

    // Target user; null when the grant targets a group.
    // Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    // Target group; null when the grant targets a user.
    // Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id", nullable = true)
    private OrganizationGroup group;

    // User who made the grant; null for a grant that no user made, such as the Everyone grants that an
    // organization gets when it is created or got from the backfill of the organizations that existed before
    // groups, and after that user is removed, because the grant outlives the user who made it.
    // Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "granted_by_user_id", nullable = true)
    private User grantedBy;

}
