package com.korur.oba.apps.kaya.hook.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.hook.dto.request.HookQueryRequest;
import com.korur.oba.apps.kaya.hook.dto.response.HookDetailResponse;
import com.korur.oba.apps.kaya.hook.dto.response.HookListResponse;
import com.korur.oba.apps.kaya.hook.entity.Hook;

/**
 * Reads the {@link Hook}s of an organization's versions, and their sizes and content through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.hook.controller.HookController} for the Rules page, and by the
 * render and release domains to load the hooks of a version.
 */
public interface HookQueryService {

    /**
     * Lists the hooks of the given versions, ordered by version, path and id. A version id of
     * another organization matches nothing.
     *
     * @param organizationId the organization of the request
     * @param request        the 1 to 20 version ids
     * @return the hooks with their byte sizes, without content
     */
    List<HookListResponse> getHooks(UUID organizationId, HookQueryRequest request);

    /**
     * Reads one hook with its whole file as UTF-8 text.
     *
     * @param organizationId the organization of the request
     * @param hookId        the hook
     * @return the hook with its content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such hook, or its blob is missing
     */
    HookDetailResponse getHook(UUID organizationId, UUID hookId);

    /**
     * Loads a hook of the organization.
     *
     * A hook of another organization answers the same 404 as an unknown id, so its existence is not
     * revealed.
     *
     * @param organizationId the organization of the request
     * @param hookId        the hook
     * @return the hook
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such hook
     */
    Hook getEntityById(UUID organizationId, UUID hookId);

    /**
     * Loads every hook of one version of the organization, ordered by path.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the hooks; empty when the version has none or belongs to another organization
     */
    List<Hook> getEntitiesByVersionId(UUID organizationId, UUID versionId);
}
