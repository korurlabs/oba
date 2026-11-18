package com.korur.oba.apps.kazan.pullrequest.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.response.ChatLinkedTicketResponse;
import com.korur.oba.connect.github.enums.GithubPullRequestState;

/**
 * Response payload for one pull request read from GitHub, for the share
 * preview: the picker row plus its branches and the ticket key they name.
 */
@Schema(description = "GitHub pull request response")
public record GithubPullRequestResponse(

        @Schema(description = "Pull request number", example = "379")
        int number,

        @Schema(description = "Title as GitHub holds it", example = "ACME-241: Sidebar refactor")
        String title,

        @Schema(description = "State on GitHub", example = "MERGED")
        GithubPullRequestState state,

        @Schema(description = "GitHub login of the pull request's author", example = "jane-doe")
        String authorLogin,

        @Schema(description = "Last update on GitHub (UTC)", example = "2026-10-27T14:03:00Z")
        Instant updatedAt,

        @Schema(description = "Link to the pull request on github.com", example = "https://github.com/korurlabs/village/pull/379")
        String url,

        @Schema(description = "Branch the pull request merges from", example = "feat/ACME-241-sidebar")
        String headRef,

        @Schema(description = "Branch the pull request merges into", example = "main")
        String baseRef,

        @Schema(description = "Upper-case ticket key in the head branch, else in the title", nullable = true,
                example = "ACME-241")
        String ticketKey,

        @Schema(description = "The ticket of ticketKey when the reader can open it", nullable = true)
        ChatLinkedTicketResponse linkedTicket
) {
}
