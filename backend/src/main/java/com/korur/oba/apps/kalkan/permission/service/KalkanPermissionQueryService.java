package com.korur.oba.apps.kalkan.permission.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.permission.dto.response.KalkanPagePermissionResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Reads the live grants of a root page with the names of their subjects.
 */
public interface KalkanPermissionQueryService {

    /**
     * Lists the live grants of a root page for a caller with MANAGE on it.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param pageId         the root page
     * @return the live grants, oldest first
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KLK_NOT_001 when the
     *         page is missing or hidden from the caller
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException KLK_SEC_001 below MANAGE
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KLK_VAL_006 when the
     *         page is not a root
     */
    List<KalkanPagePermissionResponse> getPermissions(User user, UUID organizationId, UUID pageId);

    /**
     * Loads a root page of the organization on which the caller holds MANAGE, the level that
     * reads and changes a root's grants.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param pageId         the root page
     * @return the root page
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KLK_NOT_001 when the
     *         page is missing or hidden from the caller
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException KLK_SEC_001 below MANAGE
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KLK_VAL_006 when the
     *         page is not a root
     */
    KalkanPage getManagedRoot(User user, UUID organizationId, UUID pageId);

    /**
     * Lists the live grants of a root page without an access check, for a caller that has made it.
     *
     * @param root the root page
     * @return the live grants, oldest first
     */
    List<KalkanPagePermissionResponse> getPermissionResponses(KalkanPage root);
}
