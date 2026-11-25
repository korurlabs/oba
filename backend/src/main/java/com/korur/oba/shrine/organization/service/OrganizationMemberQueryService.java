package com.korur.oba.shrine.organization.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberKindSummaryProjection;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Read operations for organization membership.
 */
public interface OrganizationMemberQueryService {

    /**
     * Lists the people of an organization with their role and status. Agents are left out: they are
     * managed and listed through the agents of the organization, not as members.
     * The member list identifies the people of a tenant, so only an active
     * member of that organization, of any role, may read it; callers check that
     * through {@link com.korur.oba.dojo.security.annotation.RequiredUserAccess}
     * before calling.
     *
     * @param user           the authenticated user requesting the list
     * @param organizationId the organization identifier
     * @return the members of kind HUMAN, in membership id order
     */
    List<OrganizationMemberResponse> listOrganizationMembers(User user, UUID organizationId);

    /**
     * Finds a member by organization and user IDs.
     * Used by other domains that need to check membership without direct repository access.
     *
     * @param organizationId the organization ID
     * @param userId         the user ID
     * @return optional organization member
     */
    Optional<OrganizationMember> findMember(UUID organizationId, UUID userId);

    /**
     * Returns the IDs of the user's colleagues: every user who is an ACTIVE member of
     * at least one ACTIVE organization in which the given user is also an ACTIVE member.
     * The given user is always included, so a user in no organization gets a set of one.
     *
     * Used by other domains that share content with the people of the same
     * organization without an organization header on the request.
     *
     * @param userId the user identifier
     * @return the colleague user IDs, the given user included
     */
    Set<UUID> getColleagueUserIds(UUID userId);

    /**
     * Lists the members of an organization who are expected to take part in it on a
     * given day: an ACTIVE membership created before joinedBefore, held by an ACTIVE
     * user account. Every role is returned, so the caller decides which roles count.
     *
     * Performs no access check; the caller verifies the reader's own access to the
     * organization first. Used by other domains that report on an organization's
     * members without loading their entities.
     *
     * @param organizationId the organization identifier
     * @param joinedBefore   memberships created at or after this instant are left out
     * @return one summary per member, in no particular order; empty when none match
     */
    List<OrganizationMemberSummaryProjection> listActiveMemberSummaries(UUID organizationId, Instant joinedBefore);

    /**
     * Lists the same members as {@link #listActiveMemberSummaries}, agents included, with whether each
     * is a person or an agent and, for an agent, its role, flow and provider. One query.
     *
     * Performs no access check. Used by other domains that offer members to pick from and badge the
     * agents among them, such as the people a board admin may add to a board.
     *
     * @param organizationId the organization identifier
     * @param joinedBefore   memberships created at or after this instant are left out
     * @return one summary per member, in no particular order; empty when none match
     */
    List<OrganizationMemberKindSummaryProjection> listActiveMemberKindSummaries(UUID organizationId,
                                                                               Instant joinedBefore);

    /**
     * Resolves the organization that content created without an organization header
     * belongs to: the user's default organization when its membership is
     * {@link com.korur.oba.shrine.organization.enums.OrganizationMemberStatus#ACTIVE} and the organization
     * {@link com.korur.oba.shrine.organization.enums.OrganizationStatus#ACTIVE}, else the active
     * organization of the user's earliest active membership, else none. One query.
     *
     * Performs no access check: the result is always an organization the user is an
     * active member of. Used by other domains, such as daily updates, that store an
     * organization on content posted without one.
     *
     * @param userId the user identifier
     * @return the organization ID, or empty when the user has no active membership of an
     *         active organization
     */
    Optional<UUID> findPrimaryActiveOrganizationId(UUID userId);

    /**
     * Counts the members who can take part in an organization now: an
     * {@link com.korur.oba.shrine.organization.enums.OrganizationMemberStatus#ACTIVE} membership held by a
     * {@link com.korur.oba.shrine.identity.enums.UserStatus#ACTIVE} user account. One query.
     *
     * Performs no access check; the caller verifies the reader's own access to the
     * organization first. Used by other domains that show how many people an
     * organization-wide space reaches, such as a public chat channel.
     *
     * @param organizationId the organization identifier
     * @return the number of such members; 0 for an unknown organization
     */
    long countActiveMembers(UUID organizationId);

    /**
     * Finds the users who hold one of the given roles in an organization through an
     * {@link com.korur.oba.shrine.organization.enums.OrganizationMemberStatus#ACTIVE} membership, with a
     * {@link com.korur.oba.shrine.identity.enums.UserStatus#ACTIVE} user account. One query.
     *
     * Performs no access check. Used by other domains that notify the people who
     * manage an organization, such as the owners and admins who see every private
     * chat channel.
     *
     * @param organizationId the organization identifier
     * @param roles          the roles; an empty set finds nobody
     * @return the user IDs, in no particular order; empty when none match
     */
    Set<UUID> findActiveUserIdsByRoles(UUID organizationId, Set<OrganizationMemberRole> roles);

    /**
     * Finds the same users as {@link #findActiveUserIdsByRoles} without agents. One query.
     *
     * Performs no access check. Used for the members of an organization's Everyone group, which never
     * includes an agent.
     *
     * @param organizationId the organization identifier
     * @param roles          the roles; an empty set finds nobody
     * @return the user IDs of kind HUMAN, in no particular order; empty when none match
     */
    Set<UUID> findActiveHumanUserIdsByRoles(UUID organizationId, Set<OrganizationMemberRole> roles);

    /**
     * Refuses when the user is the only active owner of any organization in which
     * they hold an ACTIVE membership. An organization without an active owner can no
     * longer be administered, so an account that is its last owner must not be
     * disabled or deleted until ownership has been shared or transferred.
     *
     * Used by other domains before they disable or delete a user account.
     *
     * @param userId the user identifier
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         ORG_STA_003 with the organization id, for the first organization found
     *         in which the user is the sole active owner
     */
    void ensureNotSoleOwnerOfAnyOrganization(UUID userId);
}
