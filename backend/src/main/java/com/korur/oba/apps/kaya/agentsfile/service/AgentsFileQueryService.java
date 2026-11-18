package com.korur.oba.apps.kaya.agentsfile.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.dto.request.AgentsFileQueryRequest;
import com.korur.oba.apps.kaya.agentsfile.dto.response.AgentsFileDetailResponse;
import com.korur.oba.apps.kaya.agentsfile.dto.response.AgentsFileListResponse;
import com.korur.oba.apps.kaya.agentsfile.entity.AgentsFile;

/**
 * Reads the {@link AgentsFile}s of an organization's versions, and their sizes and content through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.agentsfile.controller.AgentsFileController} for the
 * Rules page, and by the render and release domains to load the agents files of a version.
 */
public interface AgentsFileQueryService {

    /**
     * Lists the agents files of the given versions, ordered by version, placement and id. A version id
     * of another organization matches nothing.
     *
     * @param organizationId the organization of the request
     * @param request        the 1 to 20 version ids
     * @return the agents files with their byte sizes, without content
     */
    List<AgentsFileListResponse> getAgentsFiles(UUID organizationId, AgentsFileQueryRequest request);

    /**
     * Reads one agents file with its whole file as UTF-8 text.
     *
     * @param organizationId the organization of the request
     * @param agentsFileId   the agents file
     * @return the agents file with its content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such agents file, or its blob is missing
     */
    AgentsFileDetailResponse getAgentsFile(UUID organizationId, UUID agentsFileId);

    /**
     * Loads an agents file of the organization.
     *
     * A agents file of another organization answers the same 404 as an unknown id, so its existence is
     * not revealed.
     *
     * @param organizationId the organization of the request
     * @param agentsFileId   the agents file
     * @return the agents file
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such agents file
     */
    AgentsFile getEntityById(UUID organizationId, UUID agentsFileId);

    /**
     * Loads every agents file of one version of the organization, ordered by placement.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the agents files; empty when the version has none or belongs to another organization
     */
    List<AgentsFile> getEntitiesByVersionId(UUID organizationId, UUID versionId);
}
