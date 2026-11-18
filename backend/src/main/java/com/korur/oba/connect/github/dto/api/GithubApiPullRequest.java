package com.korur.oba.connect.github.dto.api;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK
import java.time.Instant;

// 4. Internal

/**
 * A pull request as GET /repos/{owner}/{repo}/pulls and GET /repos/{owner}/{repo}/pulls/{number}
 * return it. The list leaves out some detail fields, never the ones read here.
 *
 * @param number    the pull request number within its repository
 * @param title     the title
 * @param state     "open" or "closed"; a merged pull request is closed with mergedAt set
 * @param draft     whether the pull request is a draft
 * @param mergedAt  when it was merged, null when it was not
 * @param updatedAt when it last changed
 * @param user      the account that opened it
 * @param head      the branch with the changes
 * @param base      the branch it merges into
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubApiPullRequest(
        @JsonProperty("number") int number,
        @JsonProperty("title") String title,
        @JsonProperty("state") String state,
        @JsonProperty("draft") boolean draft,
        @JsonProperty("merged_at") Instant mergedAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("user") GithubApiUser user,
        @JsonProperty("head") GithubApiBranchRef head,
        @JsonProperty("base") GithubApiBranchRef base
) {
}
