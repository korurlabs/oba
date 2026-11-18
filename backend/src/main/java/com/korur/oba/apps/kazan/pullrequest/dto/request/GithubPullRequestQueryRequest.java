package com.korur.oba.apps.kazan.pullrequest.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.pullrequest.constant.PullRequestShareConstant;
import com.korur.oba.connect.github.constant.GithubConstant;
import com.korur.oba.connect.github.enums.GithubPullRequestState;

/**
 * Query parameters for the pull request picker of one repository. It does not
 * extend QueryRequest: GitHub fixes the page size at 30 and the order at last
 * update, newest first, so only the page, the search text and the two filters
 * can be chosen.
 */
@Getter
@Setter
@NoArgsConstructor
public class GithubPullRequestQueryRequest {

    /**
     * Search text. "#379" or "379" reads that pull request; other text searches
     * title and body. Null or blank lists every pull request.
     */
    @Schema(description = "\"#379\" or \"379\" finds that number; other text searches title and body", nullable = true,
            example = "sidebar")
    @Size(max = PullRequestShareConstant.MAX_SEARCH_LENGTH, message = "Search must be at most 100 characters")
    private String search;

    /**
     * States to keep, comma-separated in the query string. Null or empty lists
     * every state.
     */
    @Schema(description = "Keep pull requests in any of these states; none lists every state", nullable = true,
            example = "OPEN,DRAFT")
    private List<GithubPullRequestState> states;

    /**
     * GitHub logins to keep, comma-separated in the query string. Null or empty
     * lists every author. Each login becomes part of a GitHub search query, so
     * nothing but a login's own alphabet passes.
     */
    @Schema(description = "Keep pull requests by any of these GitHub logins, at most 5; none lists every author",
            nullable = true, example = "devssensey,github-actions[bot]")
    @Size(max = PullRequestShareConstant.MAX_AUTHOR_FILTERS, message = "At most 5 authors can be chosen")
    private List<
            @Pattern(regexp = GithubConstant.LOGIN_REGEX, message = "Author must be a GitHub login") String> authors;

    /**
     * Page number, from 1.
     */
    @Schema(description = "Page number (1-based)", nullable = true, example = "1")
    @Min(value = PullRequestShareConstant.MIN_PAGE, message = "Page must be at least 1")
    private int page = PullRequestShareConstant.MIN_PAGE;
}
