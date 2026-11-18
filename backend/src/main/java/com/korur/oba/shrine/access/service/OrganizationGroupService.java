package com.korur.oba.shrine.access.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.dto.request.OrganizationGroupCreateRequest;
import com.korur.oba.shrine.access.dto.request.OrganizationGroupMemberAddRequest;
import com.korur.oba.shrine.access.dto.request.OrganizationGroupUpdateRequest;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupMemberResponse;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupResponse;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;

/**
 * Write operations for the groups of an organization and their members.
 *
 * The Everyone group stands for every member of the organization, so it cannot be renamed, deleted or given
 * member rows. Groups and member rows are soft deleted and every change is audited. The methods perform no
 * access check; the caller checks that the actor is an OWNER or ADMIN of the organization.
 */
public interface OrganizationGroupService {

    /**
     * Creates a custom group with no members and no app.
     *
     * @param actor          the user who creates the group
     * @param organizationId the organization identifier
     * @param request        the name and the description
     * @return the new group
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException ORG_NOT_001 when the
     *         organization does not exist
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException APP_STA_002 when another
     *         group of the organization has the name, whatever its case
     */
    OrganizationGroupResponse createGroup(User actor, UUID organizationId, OrganizationGroupCreateRequest request);

    /**
     * Renames a custom group or changes its description, as a full update.
     *
     * @param actor          the user who changes the group
     * @param organizationId the organization identifier
     * @param groupId        the group identifier
     * @param request        the name and the description; a null description clears it
     * @return the changed group
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException APP_NOT_001 when the group is
     *         missing, deleted or of another organization
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException APP_STA_001 for the Everyone
     *         group; APP_STA_002 when another group of the organization has the name
     */
    OrganizationGroupResponse updateGroup(User actor, UUID organizationId, UUID groupId,
                                          OrganizationGroupUpdateRequest request);

    /**
     * Deletes a custom group with its member rows and its grants (soft delete). Each member who lost one of
     * the group's apps through it is announced with {@link com.korur.oba.shrine.access.event.AppAccessRevokedEvent},
     * once for each app lost.
     *
     * @param actor          the user who deletes the group
     * @param organizationId the organization identifier
     * @param groupId        the group identifier
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException APP_NOT_001 when the group is
     *         missing, deleted or of another organization
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException APP_STA_001 for the Everyone
     *         group
     */
    void deleteGroup(User actor, UUID organizationId, UUID groupId);

    /**
     * Adds members of the organization to a custom group. A user who is already in the group is not added
     * again.
     *
     * @param actor          the user who adds the members
     * @param organizationId the organization identifier
     * @param groupId        the group identifier
     * @param request        the users to add
     * @return every member of the group after the change, ordered by full name
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException APP_NOT_001 when the group is
     *         missing, deleted or of another organization; ORG_NOT_002 when a user is no member of the
     *         organization
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException APP_STA_001 for the Everyone
     *         group
     */
    List<OrganizationGroupMemberResponse> addMembers(User actor, UUID organizationId, UUID groupId,
                                                     OrganizationGroupMemberAddRequest request);

    /**
     * Removes a user from a custom group (soft delete). The user is announced with
     * {@link com.korur.oba.shrine.access.event.AppAccessRevokedEvent} once for each of the group's apps the
     * user lost through the change.
     *
     * @param actor          the user who removes the member
     * @param organizationId the organization identifier
     * @param groupId        the group identifier
     * @param userId         the user to remove
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException APP_NOT_001 when the group is
     *         missing, deleted or of another organization; APP_NOT_003 when the user is not in the group
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException APP_STA_001 for the Everyone
     *         group
     */
    void removeMember(User actor, UUID organizationId, UUID groupId, UUID userId);

    /**
     * Creates the Everyone group of a new organization with a grant of every {@link
     * com.korur.oba.shrine.access.enums.App}, in the caller's transaction, so every member opens every app
     * that the member's role allows until an admin revokes a grant. No user makes these grants, and no audit
     * entry is written. An organization that already has its Everyone group keeps it and its grants, and
     * nothing is made.
     *
     * @param organization the new organization
     */
    void createEveryoneGroup(Organization organization);
}
