package com.korur.oba.connect.github.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * One page of the pull request picker, newest update first.
 *
 * @param items          the pull requests of the page, at most 30
 * @param page           the page number, from 1
 * @param hasMore        whether a next page may hold more pull requests
 * @param resultsLimited whether GitHub's search found more pull requests than it serves; always
 *                       false for a plain list and a search by number
 */
public record GithubPullRequestPagePayload(
        List<GithubPullRequestSummaryPayload> items,
        int page,
        boolean hasMore,
        boolean resultsLimited
) {
}
