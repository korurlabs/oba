package com.korur.oba.connect.github.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.connect.github.enums.GithubPullRequestState;

/**
 * One pull request with its branches, read when a member selects it and again when the share is
 * sent, so the shared facts always come from GitHub.
 *
 * @param number      the pull request number within its repository
 * @param title       the title as GitHub holds it, not shortened
 * @param state       the state, with draft and merged told apart
 * @param authorLogin the login of the account that opened it
 * @param updatedAt   when it last changed
 * @param url         "https://github.com/{fullName}/pull/{number}", built, never taken from GitHub
 * @param headRef     the branch with the changes
 * @param baseRef     the branch it merges into
 */
public record GithubPullRequestPayload(
        int number,
        String title,
        GithubPullRequestState state,
        String authorLogin,
        Instant updatedAt,
        String url,
        String headRef,
        String baseRef
) {
}
