package com.korur.oba.apps.kaya.review.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.review.dto.request.ChangeSubmitRequest;
import com.korur.oba.apps.kaya.review.dto.request.ReviewRequest;
import com.korur.oba.apps.kaya.review.dto.response.ReviewResultResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;

/**
 * Puts drafts in review and records the decisions on them, writing
 * {@link com.korur.oba.apps.kaya.review.entity.Review}s. The version itself is moved by
 * {@link com.korur.oba.apps.kaya.version.service.VersionService}; every composition a change touches
 * is checked by {@link com.korur.oba.apps.kaya.release.service.ReleaseQueryService} at submit, and an
 * approval is released by {@link com.korur.oba.apps.kaya.release.service.ReleaseService} in the same
 * transaction, so a version is never approved without its releases. There is no pinning: every
 * repository that composes the owner gets the approved version.
 *
 * Called by {@link com.korur.oba.apps.kaya.review.controller.ReviewController}.
 */
public interface ReviewService {

    /**
     * Puts the proposer's draft in review. Besides the checks of
     * {@link com.korur.oba.apps.kaya.version.service.VersionService#submit}, every repository that
     * composes the owner is composed with the draft in place of the current version and checked;
     * a refusal leaves the draft a draft.
     *
     * @param user           the proposer
     * @param organizationId the organization of the request
     * @param versionId      the draft
     * @param request        the reason for the change
     * @return the version, now IN_REVIEW
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such version
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException GEN_SEC_002 when the
     *         user is not the proposer
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_002 when a
     *         rebase is needed, KAY_CFG_003 when a path comes from two owners, KAY_CFG_014 when it is
     *         not a draft, KAY_CFG_015 when another version is in review, KAY_CFG_016 when it changes
     *         nothing
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_001 when a
     *         composition breaks a rule
     */
    VersionResponse submit(User user, UUID organizationId, UUID versionId, ChangeSubmitRequest request);

    /**
     * Records a review of a version in review. APPROVE sets it APPROVED and the version it replaces
     * SUPERSEDED, moves the owner's current-version pointer to it and releases every repository
     * that composes the owner, all in one transaction. REJECT sets it back to DRAFT, so its proposer
     * can change and submit it again. COMMENT changes nothing but the reviews.
     *
     * @param user           the reviewer
     * @param organizationId the organization of the request
     * @param versionId      the version in review
     * @param request        the decision and the comment
     * @return the review, the version after it and the new releases
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 for an
     *         unknown version or one of another organization
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_017 when
     *         the version is not in review, KAY_CFG_002 when the owner moved on since the submit,
     *         KAY_CFG_003 when a composition gives a path from two owners
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException KAY_CFG_004 for the
     *         proposer, or a role below the one the decision needs
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException GEN_VAL_001 for
     *         a REJECT or COMMENT without a comment, KAY_CFG_001 when an approval breaks a rule
     */
    ReviewResultResponse review(User user, UUID organizationId, UUID versionId, ReviewRequest request);
}
