package com.korur.oba.apps.kaya.review.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.dto.QueryRequest;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.review.dto.request.ChangeQueryRequest;
import com.korur.oba.apps.kaya.review.dto.response.ChangeDetailResponse;
import com.korur.oba.apps.kaya.review.dto.response.ChangeResponse;
import com.korur.oba.apps.kaya.review.dto.response.FileHistoryResponse;

/**
 * The changes of an organization: every version with its status, source, proposer and
 * {@link com.korur.oba.apps.kaya.review.entity.Review}s, for the Changes page and its review
 * screen. Versions are read through {@link com.korur.oba.apps.kaya.version.service.VersionQueryService}
 * and the rules an approval would check through
 * {@link com.korur.oba.apps.kaya.release.service.ReleaseQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.review.controller.ReviewController}.
 */
public interface ReviewQueryService {

    /**
     * Lists the versions of the organization, most recently changed first.
     *
     * @param organizationId the organization of the request
     * @param request        paging, and the optional status, pack filters
     * @return a page of changes, each with its reviews
     */
    Page<ChangeResponse> getChanges(UUID organizationId, ChangeQueryRequest request);

    /**
     * Reads one change for its review screen: for a version in review, the rules an approval
     * would refuse it for now, and whether the caller may approve, reject or comment. For a draft
     * the caller proposed, the rules a submit would refuse it for, so the proposer sees them before
     * submitting; another member's draft carries none, since only its proposer can act on them.
     *
     * @param user           the caller
     * @param organizationId the organization of the request
     * @param versionId      the version
     * @return the change
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 for an
     *         unknown version or one of another organization
     */
    ChangeDetailResponse getChange(User user, UUID organizationId, UUID versionId);

    /**
     * The history of one file of a pack with the reviews of each version, for the
     * version menu and the history panel of a document. Which versions are read, and how a page
     * is cut, is {@link com.korur.oba.apps.kaya.version.service.VersionQueryService#getFileHistory}.
     *
     * @param user           the caller, whose own drafts are included
     * @param organizationId the organization of the request
     * @param versionId      any version of the pack
     * @param path           the path of the file relative to the repository root
     * @param page           the paging over the owner's versions
     * @return one page of versions that changed the file, newest number first, each with its
     *         reviews oldest first
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 for an
     *         unknown version or one of another organization
     */
    Page<FileHistoryResponse> getFileHistory(User user, UUID organizationId, UUID versionId, String path, QueryRequest page);
}
