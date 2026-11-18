package com.korur.oba.apps.kalkan.permission.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Decides a caller's level on a root page and the subtree below it.
 *
 * The organization role was already checked by the endpoint gate
 * ({@link com.korur.oba.dojo.security.annotation.RequiredUserAccess}), which is the only
 * organization check of a request; this service adds the resource rule the gate cannot know. It
 * reads the role only to derive the level: an OWNER holds MANAGE on every root, any other member
 * holds the level of their live grant, and a GUEST never more than VIEW.
 *
 * It depends on no page service, so the page services can call it without a bean cycle.
 */
public interface KalkanAccessQueryService {

    /**
     * Resolves a member's level on a root page.
     *
     * @param rootId the root page
     * @param user   the caller
     * @param role   the caller's organization role; null when the caller is no member
     * @return the level, or null when the caller holds none
     */
    KalkanLevel resolveLevel(UUID rootId, User user, OrganizationMemberRole role);

    /**
     * Requires at least the given level on the root of a page the page domain loaded by
     * organization and id.
     *
     * A caller without any level gets the same 404 as for a missing page, so a hidden root is
     * never confirmed.
     *
     * @param organizationId the organization of the request
     * @param page           the page, loaded within that organization
     * @param user           the caller
     * @param minimum        the level the action needs
     * @return the caller's level on the page's root, at least the minimum
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KLK_NOT_001 when the
     *         caller holds no level on the root
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException KLK_SEC_001 when the
     *         caller's level is below the minimum
     */
    KalkanLevel verifyLevel(UUID organizationId, KalkanPage page, User user, KalkanLevel minimum);

    /**
     * Requires at least the given level on a root, for a page known only by its ids. A restore
     * uses it, because the page it checks is deleted and is read without its entity.
     *
     * The grants of a deleted root stay live, so the level is the one the root had before its
     * delete.
     *
     * @param organizationId the organization of the request
     * @param pageId         the page, of that organization; named in the 404 error values
     * @param accessRootId   the page's root id, or the page's own id when it is a root
     * @param user           the caller
     * @param minimum        the level the action needs
     * @return the caller's level on the root, at least the minimum
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KLK_NOT_001 when the
     *         caller holds no level on the root
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException KLK_SEC_001 when the
     *         caller's level is below the minimum
     */
    KalkanLevel verifyLevel(UUID organizationId, UUID pageId, UUID accessRootId, User user, KalkanLevel minimum);

    /**
     * Requires the role a new root page needs: ADMIN or OWNER. A resource rule, not a repeat of the
     * gate: a create names a parent or none only in its body, so the gate admits every MEMBER.
     *
     * @param organizationId the organization of the request
     * @param user           the caller
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException KLK_SEC_001 when the
     *         caller's role is below ADMIN
     */
    void requireRootCreate(UUID organizationId, User user);

    /**
     * Resolves the caller's level on several root pages in one query.
     *
     * @param organizationId the organization of the request
     * @param rootIds        the root pages, all of that organization
     * @param user           the caller
     * @return the level per root the caller can see; roots without a level are left out
     */
    Map<UUID, KalkanLevel> resolveLevels(UUID organizationId, Collection<UUID> rootIds, User user);
}
