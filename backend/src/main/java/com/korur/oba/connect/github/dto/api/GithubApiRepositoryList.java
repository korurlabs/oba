package com.korur.oba.connect.github.dto.api;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * One page of GET /installation/repositories.
 *
 * @param totalCount   the number of repositories the installation can read
 * @param repositories the repositories of this page
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubApiRepositoryList(
        @JsonProperty("total_count") int totalCount,
        @JsonProperty("repositories") List<GithubApiRepository> repositories
) {
}
