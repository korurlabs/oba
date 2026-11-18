package com.korur.oba.shrine.access.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.dto.response.AppGrantResponse;
import com.korur.oba.shrine.access.dto.response.MemberAppAccessResponse;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.OrganizationMember;

/**
 * Read operations for app access: whether a member may open an app, which apps a member can open, and the
 * grants of an organization.
 *
 * A member has an app when the membership is ACTIVE, the organization is ACTIVE, the role is at least the
 * app's {@link App#getMinimumRole()}, and either the role is
 * {@link com.korur.oba.shrine.organization.enums.OrganizationMemberRole#OWNER} or a grant that is not
 * deleted names the member, the organization's Everyone group, or a custom group with the member in it.
 * A grant never raises a role: a member below the app's minimum role has no access whatever it names.
 * Every check reads the database; nothing is cached, so a revoke works on the next request.
 *
 * Other domains read app access only through this service, never through the access repositories.
 */
public interface AppAccessQueryService {

    /**
     * Tells whether a membership may open an app.
     *
     * A member whose role is below the app's minimum role has no access, even when a grant names the
     * member, so this check, {@link #verifyAppAccess} and {@link #listAccessibleApps} agree on every path,
     * machine tokens included.
     *
     * @param member the membership, may be null
     * @param app    the app
     * @return true when the member may open the app; false for a null membership
     */
    boolean hasAppAccess(OrganizationMember member, App app);

    /**
     * Tells whether a user may open an app in an organization.
     *
     * @param organizationId the organization identifier
     * @param userId         the user identifier
     * @param app            the app
     * @return true when the user is a member who may open the app; false when the user is no member
     */
    boolean hasAppAccess(UUID organizationId, UUID userId, App app);

    /**
     * Finds which of the given users may open an app in an organization, with one query whatever the
     * number of users. It applies the same rule as {@link #hasAppAccess(UUID, UUID, App)} to every user, so
     * a user is in the result exactly when that check answers true; a list of users reads it once instead
     * of once per user.
     *
     * @param organizationId the organization identifier
     * @param app            the app
     * @param userIds        the users to check; empty returns an empty set without a query
     * @return the ids of the users who may open the app; a user who is no member is left out
     */
    Set<UUID> findUserIdsWithAppAccess(UUID organizationId, App app, Collection<UUID> userIds);

    /**
     * Refuses a user who may not open an app in an organization. Used by entry points that the request
     * interceptor does not check, such as machine-token paths.
     *
     * @param organizationId the organization identifier
     * @param userId         the user identifier
     * @param app            the app
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException APP_SEC_001 with the
     *         organization id and the app, when the user may not open the app
     */
    void verifyAppAccess(UUID organizationId, UUID userId, App app);

    /**
     * Lists the apps a membership can open. An app whose minimum role is above the member's role is left
     * out even when a grant names the member, so the list shows only apps the member can use.
     *
     * @param member the membership, may be null
     * @return the apps, in app order; empty for a null, inactive or disabled membership
     */
    List<App> listAccessibleApps(OrganizationMember member);

    /**
     * Lists the apps a user can open in each organization of the user's memberships, with at most one
     * query whatever the number of memberships. Each list is what {@link #listAccessibleApps} answers for
     * that membership, so the organizations of the signed-in user show the same apps as the single check.
     *
     * @param user the user, with the memberships loaded or loadable in the current transaction
     * @return the apps by organization id, each list in app order; an organization whose membership or
     *         organization is not ACTIVE is left out
     */
    Map<UUID, List<App>> listAccessibleAppsByOrganization(User user);

    /**
     * Lists the grants of an organization that are not deleted. Performs no access check; the caller
     * checks that the reader is an OWNER or ADMIN of the organization.
     *
     * @param user           the authenticated user requesting the list
     * @param organizationId the organization identifier
     * @return the grants, ordered by app and then by target name
     */
    List<AppGrantResponse> listGrants(User user, UUID organizationId);

    /**
     * Lists the apps each ACTIVE member of an organization can open. Performs no access check; the caller
     * checks that the reader is an OWNER or ADMIN of the organization.
     *
     * @param user           the authenticated user requesting the list
     * @param organizationId the organization identifier
     * @return one entry per ACTIVE member, ordered by full name
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException ORG_NOT_001 when the
     *         organization does not exist
     */
    List<MemberAppAccessResponse> listMemberAccess(User user, UUID organizationId);

    /**
     * Lists the users whose access a grant can carry: the target user, or the users the target group
     * reaches through {@link OrganizationGroupQueryService#findGroupUserIds}.
     *
     * @param grant the grant
     * @return the user ids
     */
    Set<UUID> findTargetUserIds(AppGrant grant);

    /**
     * Finds, for each app, which of the given users hold it in the organization now. A write that may take
     * apps away calls it before the write and hands the result to
     * {@link AppAccessRevocationService#publishLostAppAccess} after the write.
     *
     * @param organizationId the organization identifier
     * @param apps           the apps the write may take away
     * @param userIds        the users the write may affect
     * @return the holders of each app, in app order; an app no user holds is left out
     */
    Map<App, Set<UUID>> findAppHolders(UUID organizationId, Collection<App> apps, Collection<UUID> userIds);
}
