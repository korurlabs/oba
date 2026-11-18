package com.korur.oba.shrine.access.entity;

// 1. Framework-level

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Builder;
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
import com.korur.oba.shrine.access.enums.OrganizationGroupType;
import com.korur.oba.shrine.organization.entity.Organization;

/**
 * A named set of members of one organization, to which the organization grants apps.
 *
 * Each organization has one {@link OrganizationGroupType#EVERYONE} group that stands for all of its members
 * and has no {@link OrganizationGroupMember} rows. A {@link OrganizationGroupType#CUSTOM} group lists its
 * members in {@link OrganizationGroupMember}. A name is unique in its organization, whatever its case, among
 * the groups that are not deleted.
 */
@Entity
@Table(name = "organization_groups", schema = "shrine")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class OrganizationGroup extends SoftDeleteBaseEntity {

    // Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", length = 500, nullable = true)
    private String description;

    // Kind of the group; only the organization's own setup creates an EVERYONE group.
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false)
    private OrganizationGroupType type = OrganizationGroupType.CUSTOM;

}
