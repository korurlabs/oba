package com.korur.oba.apps.kaya.subagent.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.subagent.dto.request.SubagentQueryRequest;
import com.korur.oba.apps.kaya.subagent.dto.response.SubagentDetailResponse;
import com.korur.oba.apps.kaya.subagent.dto.response.SubagentListResponse;
import com.korur.oba.apps.kaya.subagent.entity.Subagent;

/**
 * Reads the {@link Subagent}s of an organization's versions, and their sizes and content through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.subagent.controller.SubagentController} for the
 * Rules page, and by the render and release domains to load the subagents of a version.
 */
public interface SubagentQueryService {

    /**
     * Lists the subagents of the given versions, ordered by version, path and id. A version id of another organization matches nothing.
     *
     * @param organizationId the organization of the request
     * @param request        the 1 to 20 version ids
     * @return the subagents with their byte sizes, without content
     */
    List<SubagentListResponse> getSubagents(UUID organizationId, SubagentQueryRequest request);

    /**
     * Reads one subagent with its whole file as UTF-8 text.
     *
     * @param organizationId the organization of the request
     * @param subagentId     the subagent
     * @return the subagent with its content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such subagent, or its blob is missing
     */
    SubagentDetailResponse getSubagent(UUID organizationId, UUID subagentId);

    /**
     * Loads a subagent of the organization.
     *
     * A subagent of another organization answers the same 404 as an unknown id, so its existence is
     * not revealed.
     *
     * @param organizationId the organization of the request
     * @param subagentId     the subagent
     * @return the subagent
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such subagent
     */
    Subagent getEntityById(UUID organizationId, UUID subagentId);

    /**
     * Loads every subagent of one version of the organization, ordered by path.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the subagents; empty when the version has none or belongs to another organization
     */
    List<Subagent> getEntitiesByVersionId(UUID organizationId, UUID versionId);
}
