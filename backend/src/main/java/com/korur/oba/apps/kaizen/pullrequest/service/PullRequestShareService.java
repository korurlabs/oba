package com.korur.oba.apps.kaizen.pullrequest.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.pullrequest.dto.request.PullRequestShareCreateRequest;
import com.korur.oba.apps.kaizen.pullrequest.dto.request.PullRequestShareUpdateRequest;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write operations for pull request shares: share, edit, delete, approve and
 * withdraw an approval.
 *
 * Every write needs the MEMBER role, or a higher one, in the request's
 * organization, and acts on a share of that organization only; a share of
 * another organization is reported as not found. The share's card in the
 * pull requests channel is posted, marked changed and removed in the same
 * transaction as the share, so the channel never shows a share that was
 * rolled back and never loses one that was saved.
 */
public interface PullRequestShareService {

    /**
     * Shares a pull request of a repository of the GitHub installation in the
     * organization's pull requests channel. The pull request's title,
     * branches, GitHub author and ticket key are read from GitHub now and
     * kept as they are; status, note and reviewers are the team's and change
     * by edit. Reviewers are active OWNER, ADMIN or MEMBER members other than
     * the author, at most ten, kept in the request's order.
     *
     * GitHub is read before any transaction opens, so no database connection
     * waits on it. The share and its card are then saved in one transaction:
     * an organization without a pull requests channel saves nothing.
     *
     * @param user           the author
     * @param organizationId the request's organization
     * @param request        repository, number, status, note and reviewers
     * @return the saved share as the author sees it
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         VAL_PULL_REQUEST_SHARE_INVALID for a broken limit or reviewer, VAL_PULL_REQUEST_CHANNEL_ABSENT
     *         when the organization has no pull requests channel
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_GITHUB_NOT_CONFIGURED, NOT_GITHUB_REPOSITORY_FOUND or NOT_GITHUB_PULL_REQUEST_FOUND
     */
    PullRequestShareResponse createShare(User user, UUID organizationId, PullRequestShareCreateRequest request);

    /**
     * Replaces the status, note and reviewers of the user's own share and
     * marks it and its card edited. A reviewer kept by the edit keeps their
     * approval; a reviewer left out loses it with their place. GitHub is not
     * read again.
     *
     * @param user           the author
     * @param organizationId the request's organization
     * @param shareId        the share
     * @param request        status, note and reviewers
     * @return the edited share as the author sees it
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_PULL_REQUEST_SHARE_FOUND when the share is absent, deleted or of another organization
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException
     *         SEC_CANNOT_EDIT_OTHERS_SHARE when the user is not its author
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         VAL_PULL_REQUEST_SHARE_INVALID for a broken limit or reviewer
     */
    PullRequestShareResponse editShare(User user, UUID organizationId, UUID shareId,
                                       PullRequestShareUpdateRequest request);

    /**
     * Soft-deletes the user's own share and its card in the channel.
     *
     * @param user           the author
     * @param organizationId the request's organization
     * @param shareId        the share
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_PULL_REQUEST_SHARE_FOUND when the share is absent, deleted or of another organization
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException
     *         SEC_CANNOT_EDIT_OTHERS_SHARE when the user is not its author
     */
    void deleteShare(User user, UUID organizationId, UUID shareId);

    /**
     * Approves the share as one of its reviewers, or withdraws that approval,
     * at any status. Approving twice or withdrawing an absent approval changes
     * nothing; a change tells the channel's readers to refetch the card
     * without marking it edited.
     *
     * @param user           a reviewer of the share
     * @param organizationId the request's organization
     * @param shareId        the share
     * @param approved       true to approve, false to withdraw the approval
     * @return the share after the change, as the reviewer sees it
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_PULL_REQUEST_SHARE_FOUND when the share is absent, deleted or of another organization
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException
     *         SEC_NOT_PULL_REQUEST_REVIEWER when the user is not a reviewer
     */
    PullRequestShareResponse setApproval(User user, UUID organizationId, UUID shareId, boolean approved);
}
