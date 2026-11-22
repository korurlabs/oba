package com.korur.oba.shrine.legalentity.repository;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.legalentity.entity.LegalEntity;

/**
 * Spring Data repository of {@link LegalEntity}, the shrine.legal_entities table.
 *
 * Every method that looks up rows takes the organization, because a legal entity belongs to
 * exactly one organization and is only read and written within it. The inherited findById
 * is not used for that reason: it would find a legal entity of any organization.
 *
 * Only the legal entity domain uses this repository. Another domain reads legal entities
 * through {@link com.korur.oba.shrine.legalentity.service.LegalEntityQueryService}.
 */
@Repository
public interface LegalEntityRepository extends JpaRepository<LegalEntity, UUID>, JpaSpecificationExecutor<LegalEntity> {

    /**
     * Reads one legal entity within an organization, so one of another organization is not
     * found. Called by
     * {@link com.korur.oba.shrine.legalentity.validator.LegalEntityValidator#ensureLegalEntityExists}.
     *
     * @param id             the legal entity identifier
     * @param organizationId the organization that holds the legal entity
     * @return the legal entity, or empty when the organization has none with the id
     */
    Optional<LegalEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Reads every legal entity of an organization. An organization holds a handful of
     * companies, so the list is not paged.
     *
     * @param organizationId the organization that holds the legal entities
     * @param sort           the order of the rows
     * @return the legal entities of the organization; empty when it has none
     */
    List<LegalEntity> findAllByOrganizationId(UUID organizationId, Sort sort);

    /**
     * Tells whether the organization already has a legal entity with the short name, case
     * ignored. Used on create. The unique index on the lower-case short name enforces the
     * same rule; this check runs first so the client gets a clear conflict.
     *
     * @param organizationId the organization that holds the legal entities
     * @param shortName      the trimmed short name to look for
     * @return true when a legal entity of the organization has the short name
     */
    boolean existsByOrganizationIdAndShortNameIgnoreCase(UUID organizationId, String shortName);

    /**
     * Tells whether another legal entity of the organization has the short name, case
     * ignored. Used on update, where the legal entity being changed may keep its own name.
     *
     * @param organizationId the organization that holds the legal entities
     * @param shortName      the trimmed short name to look for
     * @param id             the legal entity left out of the check
     * @return true when a legal entity of the organization other than the given one has the short name
     */
    boolean existsByOrganizationIdAndShortNameIgnoreCaseAndIdNot(UUID organizationId, String shortName, UUID id);
}
