package com.korur.oba.shrine.access.entity;

// 1. Framework-level

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;
import com.korur.oba.shrine.access.enums.OrganizationGroupType;
import com.korur.oba.shrine.identity.entity.User;

/**
 * A user's place in a {@link OrganizationGroupType#CUSTOM} group. An
 * {@link OrganizationGroupType#EVERYONE} group has no rows, because membership of the organization
 * already makes a user part of it. A user is in a group once among the rows that are not deleted.
 */
@Entity
@Table(name = "organization_group_members", schema = "shrine")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class OrganizationGroupMember extends SoftDeleteBaseEntity {

    // Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private OrganizationGroup group;

    // Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

}
