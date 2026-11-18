package com.korur.oba.connect.github.dto.api;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK
import java.time.Instant;

// 4. Internal

/**
 * The pull request part of a search result: present only when the issue is a pull request.
 *
 * @param mergedAt when it was merged, null when it was not
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubApiIssuePullRequest(
        @JsonProperty("merged_at") Instant mergedAt
) {
}
