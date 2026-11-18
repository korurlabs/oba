package com.korur.oba.apps.kazan.pullrequest.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * Response payload for one page of the pull request picker. GitHub gives no
 * total for a plain list, so the page tells only whether another may follow.
 */
@Schema(description = "GitHub pull request picker page")
public record GithubPullRequestPageResponse(

        @Schema(description = "Rows of the page, last updated first")
        List<GithubPullRequestSummaryResponse> items,

        @Schema(description = "Page number (1-based)", example = "1")
        int page,

        @Schema(description = "True when a next page may hold more rows", example = "true")
        boolean hasMore,

        @Schema(description = "True when GitHub's search found more than the 1000 results it serves; always false "
                + "for a plain list and a number search", example = "false")
        boolean resultsLimited
) {
}
