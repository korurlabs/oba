package com.korur.oba.connect.github.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.connect.github.enums.GithubPullRequestState;

/**
 * One row of the pull request picker.
 *
 * @param number      the pull request number within its repository
 * @param title       the title as GitHub holds it, not shortened
 * @param state       the state, with draft and merged told apart
 * @param authorLogin the login of the account that opened it
 * @param updatedAt   when it last changed
 * @param url         "https://github.com/{fullName}/pull/{number}", built from the repository name
 *                    and the number, never taken from GitHub's payload
 */
public record GithubPullRequestSummaryPayload(
        int number,
        String title,
        GithubPullRequestState state,
        String authorLogin,
        Instant updatedAt,
        String url
) {
}
