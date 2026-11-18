package com.korur.oba.apps.kaya.role.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.role.dto.request.RoleQueryRequest;
import com.korur.oba.apps.kaya.role.dto.response.RoleDetailResponse;
import com.korur.oba.apps.kaya.role.dto.response.RoleListResponse;
import com.korur.oba.apps.kaya.role.entity.Role;

/**
 * Reads the {@link Role}s of an organization's versions, and their sizes and content through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.role.controller.RoleController} for the
 * Rules page, and by the render and release domains to load the roles of a version.
 */
public interface RoleQueryService {

    /**
     * Lists the roles of the given versions, ordered by version, path and id. A version id of another organization matches nothing.
     *
     * @param organizationId the organization of the request
     * @param request        the 1 to 20 version ids
     * @return the roles with their byte sizes, without content
     */
    List<RoleListResponse> getRoles(UUID organizationId, RoleQueryRequest request);

    /**
     * Reads one role with its whole file as UTF-8 text.
     *
     * @param organizationId the organization of the request
     * @param roleId         the role
     * @return the role with its content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such role, or its blob is missing
     */
    RoleDetailResponse getRole(UUID organizationId, UUID roleId);

    /**
     * Loads a role of the organization.
     *
     * A role of another organization answers the same 404 as an unknown id, so its existence is
     * not revealed.
     *
     * @param organizationId the organization of the request
     * @param roleId         the role
     * @return the role
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such role
     */
    Role getEntityById(UUID organizationId, UUID roleId);

    /**
     * Loads every role of one version of the organization, ordered by path.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the roles; empty when the version has none or belongs to another organization
     */
    List<Role> getEntitiesByVersionId(UUID organizationId, UUID versionId);
}
