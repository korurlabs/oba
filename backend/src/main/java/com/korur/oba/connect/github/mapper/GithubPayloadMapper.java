package com.korur.oba.connect.github.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

// 3. Java SDK
import java.time.Instant;
import java.util.List;

// 4. Internal
import com.korur.oba.connect.github.constant.GithubConstant;
import com.korur.oba.connect.github.dto.GithubContributorPayload;
import com.korur.oba.connect.github.dto.GithubPullRequestPagePayload;
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.connect.github.dto.GithubPullRequestSummaryPayload;
import com.korur.oba.connect.github.dto.GithubRepositoryPayload;
import com.korur.oba.connect.github.dto.api.GithubApiIssue;
import com.korur.oba.connect.github.dto.api.GithubApiIssuePullRequest;
import com.korur.oba.connect.github.dto.api.GithubApiPullRequest;
import com.korur.oba.connect.github.dto.api.GithubApiRepository;
import com.korur.oba.connect.github.dto.api.GithubApiUser;
import com.korur.oba.connect.github.enums.GithubPullRequestState;

/**
 * Maps GitHub's REST payloads to the payloads the rest of the application reads.
 *
 * The link of a pull request is built from the repository name of the installation's list and the
 * number, never taken from GitHub's html_url, so a card can only ever point at github.com. The
 * state tells draft and merged apart, which GitHub's own "open" and "closed" do not.
 */
@Mapper(componentModel = "spring")
public interface GithubPayloadMapper {

    /**
     * Maps one repository of the installation's list.
     */
    @Mapping(target = "githubRepositoryId", source = "id")
    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "privateRepository", source = "privateRepository")
    GithubRepositoryPayload toRepositoryPayload(GithubApiRepository repository);

    /**
     * Maps the repositories of one list page.
     */
    List<GithubRepositoryPayload> toRepositoryPayloads(List<GithubApiRepository> repositories);

    /**
     * Maps one contributor of a repository. GitHub's other account fields stay out on purpose:
     * only the login is shown or searched for.
     */
    @Mapping(target = "login", source = "login")
    GithubContributorPayload toContributorPayload(GithubApiUser user);

    /**
     * Maps one pull request of the list endpoint to a picker row.
     *
     * @param pullRequest the pull request
     * @param fullName    the repository's "owner/name", for the link
     */
    @Mapping(target = "number", source = "pullRequest.number")
    @Mapping(target = "title", source = "pullRequest.title")
    @Mapping(target = "state", source = "pullRequest", qualifiedByName = "pullRequestState")
    @Mapping(target = "authorLogin", source = "pullRequest.user.login")
    @Mapping(target = "updatedAt", source = "pullRequest.updatedAt")
    @Mapping(target = "url", source = "pullRequest.number", qualifiedByName = "pullRequestUrl")
    GithubPullRequestSummaryPayload toSummaryPayload(GithubApiPullRequest pullRequest, @Context String fullName);

    /**
     * Maps one search hit to a picker row.
     *
     * @param issue    the hit, always a pull request because the query carries "is:pr"
     * @param fullName the repository's "owner/name", for the link
     */
    @Mapping(target = "number", source = "issue.number")
    @Mapping(target = "title", source = "issue.title")
    @Mapping(target = "state", source = "issue", qualifiedByName = "issueState")
    @Mapping(target = "authorLogin", source = "issue.user.login")
    @Mapping(target = "updatedAt", source = "issue.updatedAt")
    @Mapping(target = "url", source = "issue.number", qualifiedByName = "pullRequestUrl")
    GithubPullRequestSummaryPayload toSummaryPayload(GithubApiIssue issue, @Context String fullName);

    /**
     * Maps one pull request of the detail endpoint, with its branches.
     *
     * @param pullRequest the pull request
     * @param fullName    the repository's "owner/name", for the link
     */
    @Mapping(target = "number", source = "pullRequest.number")
    @Mapping(target = "title", source = "pullRequest.title")
    @Mapping(target = "state", source = "pullRequest", qualifiedByName = "pullRequestState")
    @Mapping(target = "authorLogin", source = "pullRequest.user.login")
    @Mapping(target = "updatedAt", source = "pullRequest.updatedAt")
    @Mapping(target = "url", source = "pullRequest.number", qualifiedByName = "pullRequestUrl")
    @Mapping(target = "headRef", source = "pullRequest.head.ref")
    @Mapping(target = "baseRef", source = "pullRequest.base.ref")
    GithubPullRequestPayload toPullRequestPayload(GithubApiPullRequest pullRequest, @Context String fullName);

    /**
     * Turns a full pull request into its picker row, for a search by number.
     */
    @Mapping(target = "number", source = "number")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "authorLogin", source = "authorLogin")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "url", source = "url")
    GithubPullRequestSummaryPayload toSummaryPayload(GithubPullRequestPayload pullRequest);

    /**
     * Builds one picker page.
     *
     * @param items          the rows of the page
     * @param page           the page number, from 1
     * @param hasMore        whether a next page may hold more rows
     * @param resultsLimited whether GitHub's search found more rows than it serves
     */
    @Mapping(target = "items", source = "items")
    @Mapping(target = "page", source = "page")
    @Mapping(target = "hasMore", source = "hasMore")
    @Mapping(target = "resultsLimited", source = "resultsLimited")
    GithubPullRequestPagePayload toPagePayload(List<GithubPullRequestSummaryPayload> items, int page, boolean hasMore,
                                               boolean resultsLimited);

    /**
     * Derives the picker state of a pull request of the list or detail endpoint.
     *
     * @param pullRequest the pull request
     * @return the state
     */
    @Named("pullRequestState")
    default GithubPullRequestState toState(GithubApiPullRequest pullRequest) {
        return toState(pullRequest.state(), pullRequest.draft(), pullRequest.mergedAt());
    }

    /**
     * Derives the picker state of a search hit, whose merge time sits in its pull request part.
     *
     * @param issue the search hit
     * @return the state
     */
    @Named("issueState")
    default GithubPullRequestState toState(GithubApiIssue issue) {
        GithubApiIssuePullRequest pullRequest = issue.pullRequest();
        return toState(issue.state(), issue.draft(), pullRequest == null ? null : pullRequest.mergedAt());
    }

    /**
     * Derives the picker state: merged wins over closed, and an open draft is a draft.
     *
     * @param state    GitHub's "open" or "closed"
     * @param draft    GitHub's draft flag
     * @param mergedAt the merge time, null when not merged
     * @return the state
     */
    default GithubPullRequestState toState(String state, boolean draft, Instant mergedAt) {
        if (mergedAt != null) {
            return GithubPullRequestState.MERGED;
        }
        if (GithubConstant.STATE_OPEN.equals(state)) {
            return draft ? GithubPullRequestState.DRAFT : GithubPullRequestState.OPEN;
        }
        return GithubPullRequestState.CLOSED;
    }

    /**
     * Builds the github.com link of a pull request.
     *
     * @param number   the pull request number
     * @param fullName the repository's "owner/name"
     * @return "https://github.com/{fullName}/pull/{number}"
     */
    @Named("pullRequestUrl")
    default String toPullRequestUrl(int number, @Context String fullName) {
        return GithubConstant.WEB_BASE_URL + fullName + GithubConstant.PULL_REQUEST_URL_SEGMENT + number;
    }
}
