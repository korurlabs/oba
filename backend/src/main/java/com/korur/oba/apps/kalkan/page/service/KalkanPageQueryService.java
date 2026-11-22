package com.korur.oba.apps.kalkan.page.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageDetailResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageListResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageRecentResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageRevisionDetailResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageRevisionListResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageTrashResponse;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Reads the {@link KalkanPage}s of an organization.
 *
 * {@link #getEntity} performs no access check: callers check the caller's level on the page's root
 * through {@link com.korur.oba.apps.kalkan.permission.service.KalkanAccessQueryService} after
 * loading it. The response reads check the level themselves.
 */
public interface KalkanPageQueryService {

    /**
     * Loads a live page of the organization.
     *
     * A page of another organization answers the same 404 as an unknown id, so its existence is
     * not revealed.
     *
     * @param organizationId the organization of the request
     * @param pageId         the page
     * @return the page
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KLK_NOT_001 when the
     *         organization has no such live page
     */
    KalkanPage getEntity(UUID organizationId, UUID pageId);

    /**
     * Loads a live page by id, from any organization.
     *
     * Performs no organization filter, unlike {@link #getEntity}: the caller reads the organization
     * from the page and compares it with the request's before it checks the caller's level.
     *
     * @param pageId the page
     * @return the page
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KLK_NOT_001 when no
     *         live page has the id
     */
    KalkanPage getEntityById(UUID pageId);

    /**
     * Lists every live page under the roots the caller can see, flat, parents before children.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @return the pages; a root carries the caller's level
     */
    List<KalkanPageListResponse> getTree(User user, UUID organizationId);

    /**
     * Reads a page with its translations; needs VIEW on its root.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param pageId         the page
     * @return the page
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KLK_NOT_001 when the
     *         page is missing or hidden from the caller
     */
    KalkanPageDetailResponse getPage(User user, UUID organizationId, UUID pageId);

    /**
     * Builds the response of a live page the caller was already checked for.
     *
     * @param page  the page
     * @param level the caller's level on its root
     * @return the page with its live translations
     */
    KalkanPageDetailResponse toDetailResponse(KalkanPage page, KalkanLevel level);

    /**
     * Lists the history of a live page, every translation's revisions without their bodies; needs
     * VIEW on its root.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param pageId         the page
     * @return the revisions, newest page version first
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KLK_NOT_001 when the
     *         page is missing or hidden from the caller
     */
    List<KalkanPageRevisionListResponse> getRevisions(User user, UUID organizationId, UUID pageId);

    /**
     * Reads one revision of a live page with its body; needs VIEW on its root.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param pageId         the page
     * @param revisionId     the revision
     * @return the revision
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KLK_NOT_001 when the
     *         page is missing or hidden from the caller; KLK_NOT_002 when the page has no such
     *         revision
     */
    KalkanPageRevisionDetailResponse getRevision(User user, UUID organizationId, UUID pageId, UUID revisionId);

    /**
     * Lists the deletes the caller may undo, newest first: the top page of every delete, a deleted
     * root when the caller holds MANAGE on it and any other page when the caller holds EDIT or more
     * on its root, the same levels a restore checks.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param locale         the locale of the titles; null for each page's first title
     * @return one entry per delete
     */
    List<KalkanPageTrashResponse> getTrash(User user, UUID organizationId, KalkanLocale locale);

    /**
     * Lists the most recently changed live pages under the roots the caller can see.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param limit          the most pages to return
     * @param locale         the locale of the titles; null for each page's first title
     * @return the pages, last change first
     */
    List<KalkanPageRecentResponse> getRecent(User user, UUID organizationId, int limit, KalkanLocale locale);
}
