package com.korur.oba.connect.github.dto.api;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK
import java.time.Instant;

// 4. Internal

/**
 * One hit of GET /search/issues. The query carries "is:pr", so every hit is a pull request; its
 * merge time sits in pullRequest, not on the issue itself.
 *
 * @param number      the pull request number within its repository
 * @param title       the title
 * @param state       "open" or "closed"
 * @param draft       whether the pull request is a draft
 * @param updatedAt   when it last changed
 * @param user        the account that opened it
 * @param pullRequest the pull request part with the merge time
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubApiIssue(
        @JsonProperty("number") int number,
        @JsonProperty("title") String title,
        @JsonProperty("state") String state,
        @JsonProperty("draft") boolean draft,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("user") GithubApiUser user,
        @JsonProperty("pull_request") GithubApiIssuePullRequest pullRequest
) {
}
