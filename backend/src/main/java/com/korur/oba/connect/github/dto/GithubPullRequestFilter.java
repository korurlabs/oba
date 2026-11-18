package com.korur.oba.connect.github.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Objects;

// 4. Internal
import com.korur.oba.connect.github.enums.GithubPullRequestState;

/**
 * The narrowing of the pull request picker. A pull request passes when it is in any of the states
 * and by any of the authors; an empty list does not narrow. The states are the picker's, never a
 * share's team status.
 *
 * The authors become part of a GitHub query, so the caller validates each against
 * {@link com.korur.oba.connect.github.constant.GithubConstant#LOGIN_PATTERN} and bounds their
 * number before it builds a filter.
 *
 * @param states  the states to keep, empty for every state; null reads as empty
 * @param authors the logins to keep, empty for every author; null reads as empty
 */
public record GithubPullRequestFilter(
        List<GithubPullRequestState> states,
        List<String> authors
) {

    /**
     * Normalizes both lists, so a reader of the filter never has to guard against a null. A
     * comma-separated parameter with an empty element binds to a null entry, which narrows nothing.
     */
    public GithubPullRequestFilter {
        states = withoutNulls(states);
        authors = withoutNulls(authors);
    }

    /**
     * The filter that keeps every pull request.
     *
     * @return the empty filter
     */
    public static GithubPullRequestFilter none() {
        return new GithubPullRequestFilter(List.of(), List.of());
    }

    private static <T> List<T> withoutNulls(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }
}
