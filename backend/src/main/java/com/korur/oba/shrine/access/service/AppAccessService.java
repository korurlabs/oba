package com.korur.oba.shrine.access.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.dto.request.AppGrantCreateRequest;
import com.korur.oba.shrine.access.dto.response.AppGrantResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write operations for the app grants of an organization.
 *
 * Grants are soft deleted and every change is audited. The methods perform no access check; the caller
 * checks that the actor is an OWNER or ADMIN of the organization.
 */
public interface AppAccessService {

    /**
     * Grants an app to one member or to one group of the organization. A grant that already exists is
     * returned as it is, with no new row.
     *
     * @param actor          the user who makes the grant
     * @param organizationId the organization identifier
     * @param request        the app and exactly one target, a user or a group
     * @return the new or the existing grant
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException APP_VAL_001 when the request
     *         names no target or two targets
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException ORG_NOT_002 when the target
     *         user is no member of the organization; APP_NOT_001 when the target group is missing, deleted
     *         or of another organization
     */
    AppGrantResponse grant(User actor, UUID organizationId, AppGrantCreateRequest request);

    /**
     * Revokes an app grant (soft delete). Each user who lost the app through it is announced with
     * {@link com.korur.oba.shrine.access.event.AppAccessRevokedEvent}.
     *
     * @param actor          the user who revokes the grant
     * @param organizationId the organization identifier
     * @param grantId        the grant identifier
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException APP_NOT_002 when the grant is
     *         missing, deleted or of another organization
     */
    void revoke(User actor, UUID organizationId, UUID grantId);

    /**
     * Removes the custom group rows and the direct grants of a user in an organization (soft delete), so a
     * member who leaves or is removed and later comes back starts with no app. Runs in the caller's
     * transaction. Publishes no access revoked event, because removing or leaving a membership publishes
     * one for the whole membership.
     *
     * @param actor          the user who removes the member, or the member who leaves
     * @param organizationId the organization identifier
     * @param userId         the user whose access is removed
     */
    void removeMemberAccess(User actor, UUID organizationId, UUID userId);
}
