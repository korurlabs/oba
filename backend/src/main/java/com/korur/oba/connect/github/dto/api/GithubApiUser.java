package com.korur.oba.connect.github.dto.api;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK

// 4. Internal

/**
 * A GitHub account: the one that opened a pull request, or a contributor of a repository.
 *
 * @param login the account's login
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubApiUser(
        @JsonProperty("login") String login
) {
}
