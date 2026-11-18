package com.korur.oba.apps.kaizen.pullrequest.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.connect.github.enums.GithubPullRequestState;

/**
 * Response payload for one row of the pull request picker.
 */
@Schema(description = "GitHub pull request picker row")
public record GithubPullRequestSummaryResponse(

        @Schema(description = "Pull request number", example = "379")
        int number,

        @Schema(description = "Title as GitHub holds it", example = "ACME-241: Sidebar refactor")
        String title,

        @Schema(description = "State on GitHub", example = "OPEN")
        GithubPullRequestState state,

        @Schema(description = "GitHub login of the pull request's author", example = "jane-doe")
        String authorLogin,

        @Schema(description = "Last update on GitHub (UTC)", example = "2026-10-27T14:03:00Z")
        Instant updatedAt,

        @Schema(description = "Link to the pull request on github.com", example = "https://github.com/korurlabs/village/pull/379")
        String url
) {
}
