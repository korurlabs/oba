package com.korur.oba.apps.kazan.pullrequest.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

/**
 * Response payload for a pull request share preview: the pull request as
 * GitHub returns it and the text its card would show. Nothing is saved.
 */
@Schema(description = "Pull request share preview response")
public record PullRequestSharePreviewResponse(

        @Schema(description = "The pull request as GitHub returns it, with the reader's linked ticket")
        GithubPullRequestResponse pullRequest,

        @Schema(description = "Plain text the share's card would show, as the team posts it",
                example = "ACME-241: Sidebar refactor\nhttps://github.com/korurlabs/village/pull/379\nStatus: In Review")
        String summaryText
) {
}
