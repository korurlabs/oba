package com.korur.oba.apps.otak.icp.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.icp.enums.Industry;
import com.korur.oba.apps.otak.icp.enums.TargetPersonRole;
import com.korur.oba.apps.otak.relation.enums.Country;
import com.korur.oba.apps.otak.relation.enums.RelationSize;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing an ideal customer profile (ICP): the kind of company an
 * organization wants to win, described by industry, country, company size range and
 * the role of the person to approach.
 *
 * Every ICP belongs to one organization and is only read and written within it. Its
 * name is unique per organization with case ignored, which a unique index on the
 * lower-case name enforces. Rows are deleted outright; the database then clears the ICP link of the relations
 * that pointed at it and keeps the relations.
 */
@Entity
@Table(
        name = "icps",
        schema = "otak",
        uniqueConstraints = @UniqueConstraint(
                name = "icps_organization_id_lower_name_uk",
                columnNames = {"organization_id", "name"}))
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Icp extends BaseEntity {

    // -------------Atts-----------//

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT", nullable = true)
    private String description;

    /**
     * Organization that owns the ICP, set once on create from the request's checked
     * organization and never from a body.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    // -------------Enums-----------//

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "industry", nullable = false)
    private Industry industry;

    /**
     * Country the ICP targets; null means any country.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "country", nullable = true)
    private Country country;

    /**
     * Smallest head-count band; never above the largest when both are set, which a
     * check constraint enforces on the enum order.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "company_size_min", nullable = true)
    private RelationSize companySizeMin;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "company_size_max", nullable = true)
    private RelationSize companySizeMax;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "target_person_role", nullable = true)
    private TargetPersonRole targetPersonRole;
}
