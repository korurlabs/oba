package com.korur.oba.apps.kalkan.page.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageCreateRequest;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageMoveRequest;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageUpdateRequest;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageUpsertRequest;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageDetailResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageUpsertResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Writes the {@link com.korur.oba.apps.kalkan.page.entity.KalkanPage}s of an organization.
 *
 * Every method checks the caller's level on the page's root through
 * {@link com.korur.oba.apps.kalkan.permission.service.KalkanAccessQueryService}; the organization
 * role was checked by the endpoint gate. Nothing is erased: a delete and a restore are soft, and
 * every write that changes a title or body appends a revision.
 */
public interface KalkanPageService {

    /**
     * Creates a page with its translations. Without a parent it creates a root page, which needs
     * the ADMIN role and gives its creator MANAGE; under a parent it needs EDIT on the parent's root.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param request        the page
     * @return the created page
     */
    KalkanPageDetailResponse createPage(User user, UUID organizationId, KalkanPageCreateRequest request);

    /**
     * Changes a page's slug, compliance mark and given translations; needs EDIT and the current
     * version.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param pageId         the page
     * @param request        the change
     * @return the page after the change
     */
    KalkanPageDetailResponse updatePage(User user, UUID organizationId, UUID pageId, KalkanPageUpdateRequest request);

    /**
     * Moves a page under a new parent, or reorders a root page; needs EDIT on the source and the
     * target root and the current version.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param pageId         the page
     * @param request        the new place
     * @return the page after the move
     */
    KalkanPageDetailResponse movePage(User user, UUID organizationId, UUID pageId, KalkanPageMoveRequest request);

    /**
     * Soft-deletes a page and its live subtree in one transaction; needs EDIT, a root MANAGE. The
     * grants of a root and every attachment stay as they are.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param pageId         the page
     */
    void deletePage(User user, UUID organizationId, UUID pageId);

    /**
     * Restores a deleted page and exactly the pages its delete took; needs the level of the delete.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param pageId         the deleted page
     * @return the restored page
     */
    KalkanPageDetailResponse restorePage(User user, UUID organizationId, UUID pageId);

    /**
     * Creates or updates the page at a path, as a push does. An existing page changed in Kalkan
     * since the last push is refused unless forced; a dry run checks everything and writes nothing.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param request        the page and its path
     * @param dryRun         true to report the outcome without writing
     * @return the outcome
     */
    KalkanPageUpsertResponse upsertByPath(User user, UUID organizationId, KalkanPageUpsertRequest request,
                                          boolean dryRun);
}
