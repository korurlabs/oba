package com.korur.oba.apps.kazan.pullrequest.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.pullrequest.constant.controller.PullRequestShareControllerLogConstant;
import com.korur.oba.apps.kazan.pullrequest.constant.controller.PullRequestShareControllerResponseConstant;
import com.korur.oba.apps.kazan.pullrequest.dto.request.GithubPullRequestQueryRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubContributorResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubPullRequestPageResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubPullRequestResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubRepositoryResponse;
import com.korur.oba.apps.kazan.pullrequest.service.PullRequestShareQueryService;
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * REST controller for the GitHub pull request picker of a new share.
 *
 * Read-only: it lists the repositories of the GitHub App installation, their
 * contributors and their pull requests, and reads one pull request for the
 * share preview.
 * Every call needs the X-Organization-ID header and the MEMBER role, or a
 * higher one, in that organization, and serves only the organization the
 * GitHub integration is configured for; any other organization, or a blank
 * configuration, is told that GitHub is not connected. A repository ID is
 * resolved against the installation's own list, so no caller reaches a
 * repository the installation was not given.
 *
 * Paging, search and filter limits are checked by Bean Validation on the query
 * object and answer the generic validation error, as the daily update
 * endpoints do.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/github")
@Tag(name = "GitHub Pull Requests", description = "Read-only GitHub pull request picker for pull request shares")
public class GithubPullRequestController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(GithubPullRequestController.class);

    private final PullRequestShareQueryService pullRequestShareQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the repositories of the GitHub App installation, by name.
     */
    @GetMapping("/repositories")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List GitHub repositories",
            description = "Repositories the GitHub App installation can read, sorted by full name ignoring case; "
                    + "cached for 5 minutes")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Organization not found or caller is not a member, "
                    + "or GitHub is not connected for this organization (KAZ_NOT_016)"),
            @ApiResponse(responseCode = "500", description = "Internal server error, such as an unreadable GitHub "
                    + "App key (GHB_INT_002)"),
            @ApiResponse(responseCode = "502", description = "GitHub unreachable (GHB_UPS_001) or refused the App's "
                    + "credentials (GHB_UPS_002)"),
            @ApiResponse(responseCode = "503", description = "GitHub rate limit reached (GHB_UPS_003)")
    })
    public ResponseEntity<CustomResponse<List<GithubRepositoryResponse>>> listGithubRepositories(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.GITHUB_REPOSITORY_LIST_REQUEST);

        List<GithubRepositoryResponse> response = pullRequestShareQueryService.listGithubRepositories(user,
                organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.GITHUB_REPOSITORY_LIST_COMPLETED);

        return ResponseUtil.ok(PullRequestShareControllerResponseConstant.RESPONSE_GITHUB_REPOSITORY_LIST_RETRIEVED,
                response);
    }

    /**
     * Lists a repository's contributors, the options of the author filter.
     */
    @GetMapping("/repositories/{githubRepositoryId}/contributors")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List GitHub contributors",
            description = "Logins of one installation repository's contributors, at most 100, sorted ignoring case; "
                    + "an empty repository gives an empty list. Cached for an hour. The list holds commit authors, "
                    + "not every pull request author")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header, or a "
                    + "non-numeric repository ID"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Organization not found or caller is not a member, "
                    + "GitHub is not connected for this organization (KAZ_NOT_016), or the repository is not one "
                    + "of the installation (KAZ_NOT_017)"),
            @ApiResponse(responseCode = "500", description = "Internal server error, such as an unreadable GitHub "
                    + "App key (GHB_INT_002)"),
            @ApiResponse(responseCode = "502", description = "GitHub unreachable (GHB_UPS_001) or refused the App's "
                    + "credentials (GHB_UPS_002)"),
            @ApiResponse(responseCode = "503", description = "GitHub rate limit reached (GHB_UPS_003)")
    })
    public ResponseEntity<CustomResponse<List<GithubContributorResponse>>> listGithubContributors(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Parameter(description = "GitHub repository id, from the repository list") @PathVariable
            long githubRepositoryId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.GITHUB_CONTRIBUTOR_LIST_REQUEST);

        List<GithubContributorResponse> response = pullRequestShareQueryService.listGithubContributors(user,
                organizationId, githubRepositoryId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.GITHUB_CONTRIBUTOR_LIST_COMPLETED);

        return ResponseUtil.ok(PullRequestShareControllerResponseConstant.RESPONSE_GITHUB_CONTRIBUTOR_LIST_RETRIEVED,
                response);
    }

    /**
     * Lists one picker page of a repository's pull requests, optionally
     * narrowed by states and authors.
     */
    @GetMapping("/repositories/{githubRepositoryId}/pull-requests")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List GitHub pull requests",
            description = "Pull requests of one installation repository in every state (open, draft, merged, "
                    + "closed), newest update first, 30 per page; hasMore tells whether another page exists. "
                    + "states and authors are comma-separated and narrow the list: a row is in any of the states "
                    + "and by any of the authors, and a login with no pull request gives an empty page. "
                    + "search \"#379\" or \"379\" returns that pull request only, on page 1, and ignores both "
                    + "filters; other text searches title and body. Text, and filters a plain list cannot serve, "
                    + "use GitHub search (30 searches a minute, first 1000 results); resultsLimited says when "
                    + "it found more")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header, or a "
                    + "non-numeric repository ID"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Organization not found or caller is not a member, "
                    + "GitHub is not connected for this organization (KAZ_NOT_016), or the repository is not one "
                    + "of the installation (KAZ_NOT_017)"),
            @ApiResponse(responseCode = "422", description = "search over 100 characters, a non-numeric page or "
                    + "one below 1, an unknown state, more than 5 authors, or an author that is not a GitHub login "
                    + "(GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error, such as an unreadable GitHub "
                    + "App key (GHB_INT_002)"),
            @ApiResponse(responseCode = "502", description = "GitHub unreachable (GHB_UPS_001) or refused the App's "
                    + "credentials (GHB_UPS_002)"),
            @ApiResponse(responseCode = "503", description = "GitHub rate limit reached (GHB_UPS_003)")
    })
    public ResponseEntity<CustomResponse<GithubPullRequestPageResponse>> listGithubPullRequests(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Parameter(description = "GitHub repository id, from the repository list") @PathVariable
            long githubRepositoryId,
            @Valid @ModelAttribute @ParameterObject GithubPullRequestQueryRequest query) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.GITHUB_PULL_REQUEST_LIST_REQUEST);

        GithubPullRequestPageResponse response = pullRequestShareQueryService.listGithubPullRequests(user,
                organizationId, githubRepositoryId, query);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.GITHUB_PULL_REQUEST_LIST_COMPLETED);

        return ResponseUtil.ok(PullRequestShareControllerResponseConstant.RESPONSE_GITHUB_PULL_REQUEST_LIST_RETRIEVED,
                response);
    }

    /**
     * Reads one pull request for the share preview.
     */
    @GetMapping("/repositories/{githubRepositoryId}/pull-requests/{number}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Get GitHub pull request",
            description = "One pull request with its branches, the ticket key found in the head branch or else "
                    + "the title, and linkedTicket when the caller can open that ticket")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header, or a "
                    + "non-numeric repository ID or number"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Organization not found or caller is not a member, "
                    + "GitHub is not connected for this organization (KAZ_NOT_016), the repository is not one of "
                    + "the installation (KAZ_NOT_017), or GitHub has no such pull request (KAZ_NOT_018)"),
            @ApiResponse(responseCode = "500", description = "Internal server error, such as an unreadable GitHub "
                    + "App key (GHB_INT_002)"),
            @ApiResponse(responseCode = "502", description = "GitHub unreachable (GHB_UPS_001) or refused the App's "
                    + "credentials (GHB_UPS_002)"),
            @ApiResponse(responseCode = "503", description = "GitHub rate limit reached (GHB_UPS_003)")
    })
    public ResponseEntity<CustomResponse<GithubPullRequestResponse>> getGithubPullRequest(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Parameter(description = "GitHub repository id, from the repository list") @PathVariable
            long githubRepositoryId,
            @Parameter(description = "Pull request number") @PathVariable int number) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.GITHUB_PULL_REQUEST_GET_REQUEST);

        GithubPullRequestResponse response = pullRequestShareQueryService.getGithubPullRequest(user, organizationId,
                githubRepositoryId, number);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.GITHUB_PULL_REQUEST_GET_COMPLETED);

        return ResponseUtil.ok(PullRequestShareControllerResponseConstant.RESPONSE_GITHUB_PULL_REQUEST_RETRIEVED,
                response);
    }
}
