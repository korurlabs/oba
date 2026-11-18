package com.korur.oba.apps.kalkan.permission.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.permission.dto.request.KalkanPagePermissionRequest;
import com.korur.oba.apps.kalkan.permission.dto.response.KalkanPagePermissionResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Writes the grants of root pages. A grant is never changed in place: a changed level revokes the
 * old row and inserts a new one, so every past grant stays on record with its granter and revoker.
 */
public interface KalkanPermissionService {

    /**
     * Replaces every live grant of a root page with the given list. A subject listed with its
     * current level keeps its row; a removed subject or a changed level revokes the row; a new
     * subject or a new level inserts a row.
     *
     * @param user           the caller, who needs MANAGE on the root
     * @param organizationId the organization of the request
     * @param pageId         the root page
     * @param requests       the complete new list of grants; empty revokes all
     * @return the live grants after the change, oldest first
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KLK_NOT_001 when the
     *         page is missing or hidden from the caller
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException KLK_SEC_001 below MANAGE
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KLK_VAL_006 when the
     *         page is not a root; KLK_VAL_002 when a subject is missing, a group, listed twice, or not
     *         an ACTIVE member with an ACTIVE account; KLK_VAL_003 when a GUEST is given more than
     *         VIEW
     */
    List<KalkanPagePermissionResponse> replacePermissions(User user, UUID organizationId, UUID pageId,
                                                          List<KalkanPagePermissionRequest> requests);

    /**
     * Grants MANAGE on a new root page to its creator, in the transaction that creates the root.
     *
     * @param root   the new root page, already saved
     * @param userId the creator
     */
    void grantCreator(KalkanPage root, UUID userId);
}
