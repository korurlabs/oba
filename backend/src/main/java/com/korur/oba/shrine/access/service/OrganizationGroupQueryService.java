package com.korur.oba.shrine.access.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.dto.response.OrganizationGroupMemberResponse;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupResponse;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read operations for the groups of an organization and their members.
 *
 * A group is read by its id and the organization of the request, so a group of another organization, or a
 * deleted one, is not found and its id reveals nothing. The methods perform no access check; the caller
 * checks that the reader is an OWNER or ADMIN of the organization.
 */
public interface OrganizationGroupQueryService {

    /**
     * Lists the groups of an organization with their member counts and granted apps.
     *
     * @param user           the authenticated user requesting the list
     * @param organizationId the organization identifier
     * @return the groups, the Everyone group first, then by name whatever its case
     */
    List<OrganizationGroupResponse> listGroups(User user, UUID organizationId);

    /**
     * Lists the members of a group. The Everyone group has no member rows, so it lists every ACTIVE member
     * of the organization; a custom group lists its member rows.
     *
     * @param user           the authenticated user requesting the list
     * @param organizationId the organization identifier
     * @param groupId        the group identifier
     * @return the members, ordered by full name
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException APP_NOT_001 when the group
     *         is missing, deleted or of another organization
     */
    List<OrganizationGroupMemberResponse> listGroupMembers(User user, UUID organizationId, UUID groupId);

    /**
     * Loads a group of an organization for a write of the group services.
     *
     * @param organizationId the organization identifier
     * @param groupId        the group identifier
     * @return the group
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException APP_NOT_001 when the group
     *         is missing, deleted or of another organization
     */
    OrganizationGroup getGroupEntity(UUID organizationId, UUID groupId);

    /**
     * Lists the users whom a grant to a group reaches. The Everyone group has no member rows, so it reaches
     * every ACTIVE member of kind HUMAN whose app access depends on grants, never an agent; a custom group
     * reaches the users of its member rows.
     *
     * @param group the group
     * @return the user ids, in no particular order
     */
    Set<UUID> findGroupUserIds(OrganizationGroup group);
}
