package com.korur.oba.shrine.organization.entity;

// 1. Framework-level
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

/**
 * Organization entity that acts as a tenant boundary for shared data.
 */
@Entity
@Table(name = "organizations", schema = "shrine")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class Organization extends SoftDeleteBaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 512, nullable = true)
    private String description;

    @Column(name = "industry", length = 100, nullable = true)
    private String industry;

    @Column(name = "company_size", length = 50, nullable = true)
    private String companySize;

    @Column(name = "country", length = 3, nullable = true)
    private String country;

    @Column(name = "website", length = 512, nullable = true)
    private String website;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    private OrganizationStatus status;

    // Fetch: LAZY -- Hibernate 6 requires EAGER only for to-one associations whose
    // target uses @SoftDelete; a collection of soft-deleted rows may load lazily.
    // Cascade: REMOVE -- soft-deletes all memberships when organization is soft-deleted.
    @Builder.Default
    @OneToMany(mappedBy = "organization", cascade = CascadeType.REMOVE)
    private List<OrganizationMember> members = new ArrayList<>();
}
