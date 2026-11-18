package com.korur.oba.connect.github.dto.api;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK

// 4. Internal

/**
 * The head or base branch of a pull request.
 *
 * @param ref the branch name, without "refs/heads/"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubApiBranchRef(
        @JsonProperty("ref") String ref
) {
}
