package com.korur.oba.shrine.legalentity.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing a legal entity, a row of shrine.legal_entities: a company of an
 * organization that signs and issues its documents, such as "Korur B.V." and "Korur Siber
 * Guvenlik Ltd. Sti." in the one organization Korur.
 *
 * Organization scoping. Every legal entity belongs to one organization and is only read and
 * written within it: every lookup goes through
 * {@link com.korur.oba.shrine.legalentity.repository.LegalEntityRepository#findByIdAndOrganizationId}
 * or
 * {@link com.korur.oba.shrine.legalentity.repository.LegalEntityRepository#findAllByOrganizationId}.
 * The short name is unique per organization with case ignored, which a unique index on the
 * lower-case short name enforces, so a picker never shows two equal labels. Deleting the
 * organization deletes its legal entities; a single row is deleted outright.
 *
 * The id and the created and updated times come from {@link BaseEntity}.
 *
 * @see com.korur.oba.shrine.legalentity.repository.LegalEntityRepository
 * @see com.korur.oba.shrine.legalentity.service.LegalEntityQueryService
 */
@Entity
@Table(name = "legal_entities", schema = "shrine")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LegalEntity extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Organization that holds the legal entity, set once on create from the request's checked
     * organization and never from a body.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /**
     * Label shown in pickers, stored trimmed; at most 100 characters. Unique per organization,
     * case ignored.
     */
    @Column(name = "short_name", length = 100, nullable = false)
    private String shortName;

    /**
     * Full registered name printed in a contract; at most 200 characters.
     */
    @Column(name = "legal_name", length = 200, nullable = false)
    private String legalName;

    /**
     * Country of registration, an ISO 3166-1 code as on the organization.
     */
    @Column(name = "country", length = 3, nullable = false)
    private String country;

    /**
     * Chamber of commerce or trade registry number; optional.
     */
    @Column(name = "registration_number", length = 50)
    private String registrationNumber;

    /**
     * VAT number; optional.
     */
    @Column(name = "vat_number", length = 50)
    private String vatNumber;

    /**
     * Street and number of the registered address; optional.
     */
    @Column(name = "street", length = 200)
    private String street;

    /**
     * Postal code of the registered address; optional.
     */
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    /**
     * City of the registered address; optional.
     */
    @Column(name = "city", length = 100)
    private String city;

    /**
     * The law the company's contracts are governed by, as the contracts name it; optional.
     */
    @Column(name = "governing_law", length = 200)
    private String governingLaw;

    /**
     * The court the company's contracts name for disputes; optional.
     */
    @Column(name = "court", length = 200)
    private String court;
}
