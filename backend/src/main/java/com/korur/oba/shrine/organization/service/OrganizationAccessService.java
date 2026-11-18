package com.korur.oba.shrine.organization.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Centralised organization access control service.
 *
 * Validates that the requested organization exists, verifies the user
 * holds at least the specified minimum role, and returns the validated
 * entity so callers can proceed without redundant lookups.
 */
public interface OrganizationAccessService {

    /**
     * Validates that the organization exists, is active, and the user has at least the minimum role.
     *
     * @param organizationId the organization identifier
     * @param userId         the authenticated user identifier
     * @param minimumRole    the minimum organization role required
     * @return the validated organization entity
     */
    Organization verifyOrganizationAccess(UUID organizationId, UUID userId, OrganizationMemberRole minimumRole);

    /**
     * Validates the organization and the role as {@link #verifyOrganizationAccess(UUID, UUID, OrganizationMemberRole)}
     * does, then that the member may open the app, on the same membership.
     *
     * For entry points of an app that the request interceptor does not check, such as WebSocket messages
     * and paths exempt from the organization check. The app rule is the one of
     * {@link AppAccessQueryService#hasAppAccess(OrganizationMember, App)}, so a member below the minimum
     * role of the app is refused: a GUEST is refused {@link App#KAYA} even when a grant names the GUEST.
     *
     * @param organizationId the organization identifier
     * @param userId         the authenticated user identifier
     * @param minimumRole    the minimum organization role required
     * @param app            the app the caller acts in
     * @return the validated organization entity
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException APP_SEC_001 with the organization
     *         id and the app, when the member may not open the app; the codes of the role check before it
     */
    Organization verifyOrganizationAccess(
            UUID organizationId, UUID userId, OrganizationMemberRole minimumRole, App app);
}
