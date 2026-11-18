package com.korur.oba.apps.kalkan.page.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageDetailResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageListResponse;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
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
}
