package com.korur.oba.connect.github.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * A contributor of a repository, an account that can be chosen as a pull request author.
 *
 * @param login the GitHub login, a bot's with its "[bot]" suffix
 */
public record GithubContributorPayload(
        String login
) {
}
