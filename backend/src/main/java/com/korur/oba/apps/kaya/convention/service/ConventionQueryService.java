package com.korur.oba.apps.kaya.convention.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.convention.dto.request.ConventionQueryRequest;
import com.korur.oba.apps.kaya.convention.dto.response.ConventionDetailResponse;
import com.korur.oba.apps.kaya.convention.dto.response.ConventionListResponse;
import com.korur.oba.apps.kaya.convention.entity.Convention;

/**
 * Reads the {@link Convention}s of an organization's versions, and their sizes and content through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.convention.controller.ConventionController} for the
 * Conventions page, and by the render, skill and release domains to load the conventions of a version.
 */
public interface ConventionQueryService {

    /**
     * Lists the conventions of the given versions, ordered by version, path and id, the skill-only
     * conventions without a path last. A version id of another organization matches nothing.
     *
     * @param organizationId the organization of the request
     * @param request        the 1 to 20 version ids
     * @return the conventions with their byte sizes, without content
     */
    List<ConventionListResponse> getConventions(UUID organizationId, ConventionQueryRequest request);

    /**
     * Reads one convention with its whole file as UTF-8 text.
     *
     * @param organizationId the organization of the request
     * @param conventionId   the convention
     * @return the convention with its content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such convention, or its blob is missing
     */
    ConventionDetailResponse getConvention(UUID organizationId, UUID conventionId);

    /**
     * Loads a convention of the organization.
     *
     * A convention of another organization answers the same 404 as an unknown id, so its existence is
     * not revealed.
     *
     * @param organizationId the organization of the request
     * @param conventionId   the convention
     * @return the convention
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such convention
     */
    Convention getEntityById(UUID organizationId, UUID conventionId);

    /**
     * Loads every convention of one version of the organization, ordered by path.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the conventions; empty when the version has none or belongs to another organization
     */
    List<Convention> getEntitiesByVersionId(UUID organizationId, UUID versionId);
}
