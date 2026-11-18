package com.korur.oba.apps.kaya.identity.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Decides whether a user may act in an organization. Every organization call runs this check.
 */
public interface OrganizationAccessQueryService {

    /**
     * Checks that the platform user is ACTIVE, holds an ACTIVE membership of the organization with
     * a role above GUEST, and that the organization is ACTIVE.
     *
     * @param user           the signed-in user
     * @param organizationId the organization of the X-Organization-ID header
     * @return the membership, with its organization loaded
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException with GEN_SEC_002 when
     *         the user is not ACTIVE or not a member, and with KAY_SEC_002 when the membership or
     *         the organization is not ACTIVE, and with GEN_SEC_003 when the member is a GUEST
     */
    OrganizationMember verifyTenantAccess(User user, UUID organizationId);
}
