package com.korur.oba.connect.github.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * A repository the GitHub App installation can read.
 *
 * @param githubRepositoryId GitHub's numeric repository ID, stable across renames
 * @param fullName           "owner/name"
 * @param privateRepository  whether the repository is private
 */
public record GithubRepositoryPayload(
        long githubRepositoryId,
        String fullName,
        boolean privateRepository
) {
}
