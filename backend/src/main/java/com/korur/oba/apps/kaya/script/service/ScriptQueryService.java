package com.korur.oba.apps.kaya.script.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.script.dto.response.ScriptDetailResponse;
import com.korur.oba.apps.kaya.script.entity.Script;

/**
 * Reads the {@link Script}s of an organization's versions, and their size and content through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.script.controller.ScriptController} for a script opened from a
 * skill, and by the render and skill domains to load the scripts of a version.
 */
public interface ScriptQueryService {

    /**
     * Reads one script with its whole file as UTF-8 text.
     *
     * @param organizationId the organization of the request
     * @param scriptId       the script
     * @return the script with its content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such script, or its blob is missing
     */
    ScriptDetailResponse getScript(UUID organizationId, UUID scriptId);

    /**
     * Loads a script of the organization.
     *
     * A script of another organization answers the same 404 as an unknown id, so its existence is not
     * revealed.
     *
     * @param organizationId the organization of the request
     * @param scriptId       the script
     * @return the script
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such script
     */
    Script getEntityById(UUID organizationId, UUID scriptId);

    /**
     * Loads every script of one version of the organization, ordered by name and id.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the scripts; empty when the version has none or belongs to another organization
     */
    List<Script> getEntitiesByVersionId(UUID organizationId, UUID versionId);
}
