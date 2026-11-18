package com.korur.oba.connect.github.dto.api;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * One page of GET /search/issues.
 *
 * @param totalCount the number of hits; GitHub serves at most the first 1000 of them
 * @param items      the hits of this page
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubApiIssueSearch(
        @JsonProperty("total_count") int totalCount,
        @JsonProperty("items") List<GithubApiIssue> items
) {
}
