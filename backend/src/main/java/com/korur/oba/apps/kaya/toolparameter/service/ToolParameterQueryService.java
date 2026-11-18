package com.korur.oba.apps.kaya.toolparameter.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.toolparameter.dto.response.ToolParameterDetailResponse;
import com.korur.oba.apps.kaya.toolparameter.entity.ToolParameter;

/**
 * Reads the {@link ToolParameter}s of an organization's versions, and their size and content through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.toolparameter.controller.ToolParameterController} for a tool parameter file opened from a
 * skill, and by the render and skill domains to load the tool parameter files of a version.
 */
public interface ToolParameterQueryService {

    /**
     * Reads one tool parameter file with its whole file as UTF-8 text.
     *
     * @param organizationId the organization of the request
     * @param toolParameterId the tool parameter file
     * @return the tool parameter file with its content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such tool parameter file, or its blob is missing
     */
    ToolParameterDetailResponse getToolParameter(UUID organizationId, UUID toolParameterId);

    /**
     * Loads a tool parameter file of the organization.
     *
     * A tool parameter file of another organization answers the same 404 as an unknown id, so its existence is not
     * revealed.
     *
     * @param organizationId the organization of the request
     * @param toolParameterId the tool parameter file
     * @return the tool parameter file
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such tool parameter file
     */
    ToolParameter getEntityById(UUID organizationId, UUID toolParameterId);

    /**
     * Loads every tool parameter file of one version of the organization, ordered by name and id.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the tool parameter files; empty when the version has none or belongs to another organization
     */
    List<ToolParameter> getEntitiesByVersionId(UUID organizationId, UUID versionId);
}
