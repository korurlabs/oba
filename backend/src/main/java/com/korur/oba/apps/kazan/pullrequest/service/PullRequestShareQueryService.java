package com.korur.oba.apps.kazan.pullrequest.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.pullrequest.dto.request.GithubPullRequestQueryRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.request.PullRequestShareCreateRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.request.PullRequestShareQueryRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubContributorResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubPullRequestPageResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubPullRequestResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubRepositoryResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestSharePreviewResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShare;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read operations for pull request shares and for the GitHub picker that
 * chooses the pull request of a new share.
 *
 * Every read for a user needs the MEMBER role, or a higher one, in the
 * request's organization. A share is read only within its organization; one
 * of another organization is reported as not found. The GitHub reads serve
 * only the organization the integration is configured for, and only the
 * repositories of its installation.
 */
public interface PullRequestShareQueryService {

    /**
     * Lists the repositories of the GitHub App installation, by name.
     *
     * @param user           the current user
     * @param organizationId the request's organization
     * @return the repositories
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_GITHUB_NOT_CONFIGURED when GitHub is not set up for the organization
     */
    List<GithubRepositoryResponse> listGithubRepositories(User user, UUID organizationId);

    /**
     * Lists a repository's contributors, the options of the picker's author
     * filter, by login. The list holds commit authors, not every pull request
     * author, and can lag GitHub by hours.
     *
     * @param user               the current user
     * @param organizationId     the request's organization
     * @param githubRepositoryId GitHub's ID of a repository of the installation
     * @return the contributors, at most 100; empty for an empty repository
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_GITHUB_NOT_CONFIGURED, or NOT_GITHUB_REPOSITORY_FOUND for a repository outside the installation
     */
    List<GithubContributorResponse> listGithubContributors(User user, UUID organizationId, long githubRepositoryId);

    /**
     * Lists one picker page of a repository's pull requests, newest update
     * first, optionally narrowed by states and authors and searched by number
     * or text. A search by number ignores the filters.
     *
     * @param user               the current user
     * @param organizationId     the request's organization
     * @param githubRepositoryId GitHub's ID of a repository of the installation
     * @param request            search text, filters and page
     * @return the page, at most 30 rows
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_GITHUB_NOT_CONFIGURED, or NOT_GITHUB_REPOSITORY_FOUND for a repository outside the installation
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         VAL_PULL_REQUEST_SHARE_INVALID for a search over 100 characters, more than 5 authors, an author
     *         that is not a GitHub login or a page below 1
     */
    GithubPullRequestPageResponse listGithubPullRequests(User user, UUID organizationId, long githubRepositoryId,
                                                         GithubPullRequestQueryRequest request);

    /**
     * Reads one pull request for the share preview, with its branches, the
     * ticket key of its branch or title and the ticket when the user can open it.
     *
     * @param user               the current user
     * @param organizationId     the request's organization
     * @param githubRepositoryId GitHub's ID of a repository of the installation
     * @param number             the pull request number
     * @return the pull request
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_GITHUB_NOT_CONFIGURED, NOT_GITHUB_REPOSITORY_FOUND or NOT_GITHUB_PULL_REQUEST_FOUND
     */
    GithubPullRequestResponse getGithubPullRequest(User user, UUID organizationId, long githubRepositoryId,
                                                   int number);

    /**
     * Retrieves one live share of the organization.
     *
     * @param user           the reader
     * @param organizationId the request's organization
     * @param shareId        the share
     * @return the share as the reader sees it
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_PULL_REQUEST_SHARE_FOUND when the share is absent, deleted or of another organization
     */
    PullRequestShareResponse getShare(User user, UUID organizationId, UUID shareId);

    /**
     * Lists one page of the organization's live shares, newest share first,
     * narrowed by status, author and whether they wait for the reader's
     * review. An edit does not move a share.
     *
     * @param user           the reader
     * @param organizationId the request's organization
     * @param request        filters and page; its sort fields are ignored
     * @return the page of shares as the reader sees them
     */
    Page<PullRequestShareResponse> listShares(User user, UUID organizationId, PullRequestShareQueryRequest request);

    /**
     * Previews a new share without saving it: runs every check of a create,
     * in the same order, and returns the pull request with the text its card
     * would show.
     *
     * @param user           the member who would share it
     * @param organizationId the request's organization
     * @param request        the create request
     * @return the pull request and the card's text
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         VAL_PULL_REQUEST_SHARE_INVALID for a breached limit or an ineligible reviewer, or
     *         VAL_PULL_REQUEST_CHANNEL_ABSENT when the organization has no pull requests channel
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_GITHUB_NOT_CONFIGURED, NOT_GITHUB_REPOSITORY_FOUND or NOT_GITHUB_PULL_REQUEST_FOUND
     */
    PullRequestSharePreviewResponse previewShare(User user, UUID organizationId,
                                                 PullRequestShareCreateRequest request);

    /**
     * Retrieves several live shares of one organization for one reader, with
     * the same per-reader linked ticket as {@link #getShare}, in a fixed
     * number of queries whatever the number of shares.
     *
     * Performs no access check: the caller has already checked that the reader
     * may read the channel the shares appear in. An ID that is absent from the
     * result belongs to a deleted share or to one of another organization; the
     * method never throws for it. Used by the chat, which shows shares inside
     * a page of its messages.
     *
     * @param reader         the reader
     * @param organizationId the organization of the chat channel
     * @param shareIds       the share IDs; null or empty returns an empty map
     * @return the live shares keyed by ID, in the order of shareIds
     */
    Map<UUID, PullRequestShareResponse> getSharesByIds(User reader, UUID organizationId, Collection<UUID> shareIds);

    /**
     * Finds a live share of the organization, without a reader. Used by the
     * chat, which posts a share's entry only for its author and in its
     * organization.
     *
     * @param organizationId the organization
     * @param shareId        the share
     * @return the share; empty when absent, deleted or of another organization
     */
    Optional<PullRequestShare> findShare(UUID organizationId, UUID shareId);
}
