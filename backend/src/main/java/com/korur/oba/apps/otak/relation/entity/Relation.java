package com.korur.oba.apps.otak.relation.entity;

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
import java.math.BigDecimal;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.enums.Country;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationSize;
import com.korur.oba.apps.otak.relation.enums.RelationSource;
import com.korur.oba.apps.otak.relation.enums.RelationType;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing a company an organization deals with: a lead it tries to win,
 * a customer or a supplier.
 *
 * Every relation belongs to one organization and is only read and written within it.
 * A lead and a customer have a stage, a supplier none; a check constraint holds each
 * type to its own stages, so the type and the stage always change together. Rows are deleted outright; the database removes the
 * relation's contacts with it.
 */
@Entity
@Table(name = "relations", schema = "otak")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Relation extends BaseEntity {

    // -------------Atts-----------//

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "website", length = 500, nullable = true)
    private String website;

    @Column(name = "sector", length = 100, nullable = true)
    private String sector;

    @Column(name = "registration_number", length = 50, nullable = true)
    private String registrationNumber;

    @Column(name = "vat_number", length = 50, nullable = true)
    private String vatNumber;

    @Column(name = "street", length = 200, nullable = true)
    private String street;

    @Column(name = "postal_code", length = 20, nullable = true)
    private String postalCode;

    @Column(name = "city", length = 100, nullable = true)
    private String city;

    /**
     * Expected value of the deal in euros; never negative.
     */
    @Column(name = "expected_value", precision = 12, scale = 2, nullable = true)
    private BigDecimal expectedValue;

    @Column(name = "notes", columnDefinition = "TEXT", nullable = true)
    private String notes;

    /**
     * Organization that owns the relation, set once on create from the request's checked
     * organization and never from a body.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /**
     * Member of the organization who looks after the relation. A plain id, not an
     * association: the user belongs to the platform core, and deleting the user clears it.
     */
    @Column(name = "owner_user_id", nullable = true)
    private UUID ownerUserId;

    /**
     * ICP the relation belongs to, of the same organization. A plain id, not an association:
     * the ICP domain is read through its query service, and deleting the ICP clears it.
     */
    @Column(name = "icp_id", nullable = true)
    private UUID icpId;

    /**
     * Event where the organization met the company, of the same organization. A plain id,
     * not an association: the event domain is read through its query service, and deleting
     * the event clears it.
     */
    @Column(name = "event_id", nullable = true)
    private UUID eventId;

    // -------------Enums-----------//

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false)
    private RelationType type;

    /**
     * Stage on the battlefield: one of {@link PipelineStage#LEAD_STAGES} for a lead, one of
     * {@link PipelineStage#CUSTOMER_STAGES} for a customer, null for a supplier.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "stage", nullable = true)
    private PipelineStage stage;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "size", nullable = true)
    private RelationSize size;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "country", nullable = true)
    private Country country;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "source", nullable = true)
    private RelationSource source;
}
