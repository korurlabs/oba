package com.korur.oba.shrine.legalentity.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.legalentity.dto.request.LegalEntityCreateRequest;
import com.korur.oba.shrine.legalentity.dto.request.LegalEntityUpdateRequest;
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityDetailResponse;

/**
 * Write side of the legal entity domain: create, update and delete. Every write is scoped to
 * the organization the caller acts in, taken from the checked organization header; the
 * controller's role check (ADMIN) has already run. Reads live in {@link LegalEntityQueryService}.
 *
 * @see com.korur.oba.shrine.legalentity.service.impl.LegalEntityServiceImpl
 */
public interface LegalEntityService {

    /**
     * Creates a legal entity of the organization with every field trimmed.
     *
     * @param organizationId the organization the caller acts in
     * @param user           the caller, for the log line
     * @param request        the validated fields
     * @return the stored legal entity
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException LGE_STA_001 when another legal
     *         entity of the organization has the short name, case ignored
     */
    LegalEntityDetailResponse create(UUID organizationId, User user, LegalEntityCreateRequest request);

    /**
     * Replaces every field of a legal entity of the organization.
     *
     * @param organizationId the organization the caller acts in
     * @param legalEntityId  the legal entity identifier
     * @param request        the validated fields
     * @return the stored legal entity
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException LGE_NOT_001 when the organization
     *         has no legal entity with the id
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException LGE_STA_001 when another legal
     *         entity of the organization has the short name, case ignored
     */
    LegalEntityDetailResponse update(UUID organizationId, UUID legalEntityId, LegalEntityUpdateRequest request);

    /**
     * Deletes a legal entity of the organization outright.
     *
     * @param organizationId the organization the caller acts in
     * @param legalEntityId  the legal entity identifier
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException LGE_NOT_001 when the organization
     *         has no legal entity with the id
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException LGE_STA_002 when a document or
     *         document template of the organization names it
     */
    void delete(UUID organizationId, UUID legalEntityId);
}
