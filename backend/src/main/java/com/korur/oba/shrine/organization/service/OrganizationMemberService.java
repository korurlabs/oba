package com.korur.oba.shrine.organization.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.dto.request.OrganizationMemberRoleUpdateRequest;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

/**
 * Write operations for organization membership management.
 */
public interface OrganizationMemberService {

    /**
     * Removes a member from an organization (soft delete).
     * The membership record becomes invisible to all queries.
     * Requires ADMIN or OWNER role.
     */
    void removeMember(User actor, UUID organizationId, UUID userId);

    /**
     * Restores a previously soft-deleted membership.
     * Requires ADMIN or OWNER role.
     */
    void restoreMember(User actor, UUID organizationId, UUID userId);

    /**
     * Updates a member's lifecycle status (enable/disable).
     * DISABLED: suspends the member's access.
     * ACTIVE: reactivates a disabled member.
     * Requires ADMIN or OWNER role.
     */
    void updateMemberStatus(User actor, UUID organizationId, UUID userId, OrganizationMemberStatus newStatus);

    /**
     * Allows a user to leave an organization voluntarily.
     * Fails if the user is the sole OWNER.
     */
    void leaveOrganization(User user, UUID organizationId);

    /**
     * Changes a member's role in an organization.
     * Requires ADMIN or OWNER role.
     */
    OrganizationMemberResponse changeMemberRole(User user, UUID organizationId, UUID userId,
            OrganizationMemberRoleUpdateRequest request);

    /**
     * Persists an organization member entity.
     * Used by other domains that manage membership without full access control checks.
     */
    OrganizationMember saveMember(OrganizationMember member);
}
