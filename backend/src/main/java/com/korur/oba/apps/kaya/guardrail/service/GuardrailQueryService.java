package com.korur.oba.apps.kaya.guardrail.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.guardrail.dto.request.GuardrailQueryRequest;
import com.korur.oba.apps.kaya.guardrail.dto.response.GuardrailDetailResponse;
import com.korur.oba.apps.kaya.guardrail.dto.response.GuardrailListResponse;
import com.korur.oba.apps.kaya.guardrail.entity.Guardrail;

/**
 * Reads the {@link Guardrail}s of an organization's versions, and their sizes and content through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.guardrail.controller.GuardrailController} for the Guardrails
 * page, and by the render and release domains to load the guardrails of a version.
 */
public interface GuardrailQueryService {

    /**
     * Lists the guardrails of the given versions, ordered by version, name and id. A version id of
     * another organization matches nothing.
     *
     * @param organizationId the organization of the request
     * @param request        the 1 to 20 version ids
     * @return the guardrails with their enforcement and byte sizes, without content
     */
    List<GuardrailListResponse> getGuardrails(UUID organizationId, GuardrailQueryRequest request);

    /**
     * Reads one guardrail with its whole file as UTF-8 text.
     *
     * @param organizationId the organization of the request
     * @param guardrailId    the guardrail
     * @return the guardrail with its content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such guardrail, or its blob is missing
     */
    GuardrailDetailResponse getGuardrail(UUID organizationId, UUID guardrailId);

    /**
     * Loads a guardrail of the organization.
     *
     * A guardrail of another organization answers the same 404 as an unknown id, so its existence is
     * not revealed.
     *
     * @param organizationId the organization of the request
     * @param guardrailId    the guardrail
     * @return the guardrail
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such guardrail
     */
    Guardrail getEntityById(UUID organizationId, UUID guardrailId);

    /**
     * Loads every guardrail of one version of the organization, ordered by name.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the guardrails; empty when the version has none or belongs to another organization
     */
    List<Guardrail> getEntitiesByVersionId(UUID organizationId, UUID versionId);
}
