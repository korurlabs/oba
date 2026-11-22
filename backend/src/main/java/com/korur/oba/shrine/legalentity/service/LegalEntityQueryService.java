package com.korur.oba.shrine.legalentity.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityDetailResponse;
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityListResponse;
import com.korur.oba.shrine.legalentity.entity.LegalEntity;

/**
 * Read side of the legal entity domain: the list, the detail and the entity another domain
 * checks a legal entity id against. Every read is scoped to the organization the caller acts
 * in, and a legal entity of another organization is answered exactly like one that does not
 * exist. Writes live in {@link LegalEntityService}.
 *
 * Other domains read legal entities through this interface and never through
 * {@link com.korur.oba.shrine.legalentity.repository.LegalEntityRepository}.
 *
 * @see com.korur.oba.shrine.legalentity.service.impl.LegalEntityQueryServiceImpl
 */
public interface LegalEntityQueryService {

    /**
     * Lists every legal entity of the organization, sorted by short name with case ignored.
     * Not paged: an organization holds a handful of companies.
     *
     * @param organizationId the organization the caller acts in
     * @return the list items; empty when the organization has none
     */
    List<LegalEntityListResponse> list(UUID organizationId);

    /**
     * Reads one legal entity of the organization with every field.
     *
     * @param organizationId the organization the caller acts in
     * @param legalEntityId  the legal entity identifier
     * @return the detail
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException LGE_NOT_001 when the organization
     *         has no legal entity with the id, also when the id belongs to another organization
     */
    LegalEntityDetailResponse get(UUID organizationId, UUID legalEntityId);

    /**
     * Reads one legal entity entity of the organization, for another domain that must prove a
     * legal entity id belongs to the organization before it stores it. The caller must not
     * change the entity.
     *
     * @param organizationId the organization the caller acts in
     * @param legalEntityId  the legal entity identifier
     * @return the legal entity
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException LGE_NOT_001 when the organization
     *         has no legal entity with the id, also when the id belongs to another organization
     */
    LegalEntity getEntity(UUID organizationId, UUID legalEntityId);
}
