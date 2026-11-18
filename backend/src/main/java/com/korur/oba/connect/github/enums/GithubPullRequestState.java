package com.korur.oba.connect.github.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The state of a pull request as the picker shows it. GitHub itself knows only "open" and
 * "closed"; a draft is an open pull request with the draft flag, and a merged one is a closed
 * pull request with a merge time.
 */
public enum GithubPullRequestState {
    OPEN,
    DRAFT,
    MERGED,
    CLOSED
}
