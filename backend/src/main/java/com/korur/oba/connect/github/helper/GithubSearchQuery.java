package com.korur.oba.connect.github.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.connect.github.constant.GithubConstant;
import com.korur.oba.connect.github.dto.GithubPullRequestFilter;
import com.korur.oba.connect.github.enums.GithubPullRequestState;

/**
 * Turns a {@link GithubPullRequestFilter} into what GitHub can answer: a state of the pull request
 * list, which spends no search limit, or a query of the search API.
 *
 * The states split in two halves that GitHub keeps apart: open (ready or draft) and closed (merged
 * or not). A choice within one half is one term; a choice across both joins the two halves with
 * OR, and every state chosen narrows nothing. Authors are alternatives too, so a query holds at
 * most four author ORs and one state OR, within GitHub's limit of five operators.
 */
public final class GithubSearchQuery {

    private GithubSearchQuery() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Finds the state of the pull request list that serves this filter exactly.
     *
     * @param filter the filter
     * @return GitHub's state value, or empty when only the search API can serve the filter
     */
    public static Optional<String> listState(GithubPullRequestFilter filter) {
        if (!filter.authors().isEmpty()) {
            return Optional.empty();
        }
        Set<GithubPullRequestState> states = toSet(filter);
        if (narrowsNothing(states)) {
            return Optional.of(GithubConstant.STATE_ALL);
        }
        if (states.equals(EnumSet.of(GithubPullRequestState.OPEN, GithubPullRequestState.DRAFT))) {
            return Optional.of(GithubConstant.STATE_OPEN);
        }
        if (states.equals(EnumSet.of(GithubPullRequestState.MERGED, GithubPullRequestState.CLOSED))) {
            return Optional.of(GithubConstant.STATE_CLOSED);
        }
        return Optional.empty();
    }

    /**
     * Tells whether the query of this filter holds an OR, which classic search answers with zero
     * rows and no error.
     *
     * @param filter the filter
     * @return true when the search needs GitHub's advanced mode
     */
    public static boolean needsAdvancedSearch(GithubPullRequestFilter filter) {
        return filterTerms(filter).stream().anyMatch(term -> term.contains(GithubConstant.SEARCH_OPERATOR_OR));
    }

    /**
     * Builds the search query: the repository, the author term, the state term and the text.
     *
     * @param fullName the repository's "owner/name", from the installation's list
     * @param text     the cleaned search text, empty for none
     * @param filter   the filter
     * @return the value of the q parameter
     */
    public static String build(String fullName, String text, GithubPullRequestFilter filter) {
        List<String> terms = new ArrayList<>();
        terms.add(GithubConstant.SEARCH_QUALIFIER_REPOSITORY + fullName);
        terms.add(GithubConstant.SEARCH_QUALIFIER_PULL_REQUEST);
        terms.addAll(filterTerms(filter));
        if (!text.isEmpty()) {
            terms.add(text);
        }
        return String.join(GithubConstant.SEARCH_TERM_SEPARATOR, terms);
    }

    private static List<String> filterTerms(GithubPullRequestFilter filter) {
        List<String> terms = new ArrayList<>();
        authorTerm(filter.authors()).ifPresent(terms::add);
        stateTerm(toSet(filter)).ifPresent(terms::add);
        return terms;
    }

    private static Optional<String> authorTerm(List<String> authors) {
        if (authors.isEmpty()) {
            return Optional.empty();
        }
        String joined = authors.stream()
                .map(GithubSearchQuery::toAuthorQualifier)
                .collect(Collectors.joining(GithubConstant.SEARCH_OPERATOR_OR));
        return Optional.of(authors.size() == 1 ? joined : group(joined));
    }

    private static String toAuthorQualifier(String login) {
        if (login.endsWith(GithubConstant.BOT_LOGIN_SUFFIX)) {
            String appName = login.substring(0, login.length() - GithubConstant.BOT_LOGIN_SUFFIX.length());
            return GithubConstant.SEARCH_QUALIFIER_APP_AUTHOR + appName;
        }
        return GithubConstant.SEARCH_QUALIFIER_AUTHOR + login;
    }

    private static Optional<String> stateTerm(Set<GithubPullRequestState> states) {
        if (narrowsNothing(states)) {
            return Optional.empty();
        }
        Optional<String> open = openTerm(states);
        Optional<String> closed = closedTerm(states);
        if (open.isPresent() && closed.isPresent()) {
            return Optional.of(group(groupIfCompound(open.get())
                    + GithubConstant.SEARCH_OPERATOR_OR + groupIfCompound(closed.get())));
        }
        return open.or(() -> closed);
    }

    private static Optional<String> openTerm(Set<GithubPullRequestState> states) {
        boolean ready = states.contains(GithubPullRequestState.OPEN);
        boolean draft = states.contains(GithubPullRequestState.DRAFT);
        if (ready && draft) {
            return Optional.of(GithubConstant.SEARCH_STATE_OPEN_ANY);
        }
        if (ready) {
            return Optional.of(GithubConstant.SEARCH_STATE_OPEN_READY);
        }
        return draft ? Optional.of(GithubConstant.SEARCH_STATE_OPEN_DRAFT) : Optional.empty();
    }

    private static Optional<String> closedTerm(Set<GithubPullRequestState> states) {
        boolean merged = states.contains(GithubPullRequestState.MERGED);
        boolean closed = states.contains(GithubPullRequestState.CLOSED);
        if (merged && closed) {
            return Optional.of(GithubConstant.SEARCH_STATE_CLOSED_ANY);
        }
        if (merged) {
            return Optional.of(GithubConstant.SEARCH_STATE_MERGED);
        }
        return closed ? Optional.of(GithubConstant.SEARCH_STATE_CLOSED_UNMERGED) : Optional.empty();
    }

    /**
     * Keeps the two qualifiers of one state together, so OR joins them as one alternative.
     */
    private static String groupIfCompound(String term) {
        return term.contains(GithubConstant.SEARCH_TERM_SEPARATOR) ? group(term) : term;
    }

    private static String group(String term) {
        return GithubConstant.SEARCH_GROUP_START + term + GithubConstant.SEARCH_GROUP_END;
    }

    private static boolean narrowsNothing(Set<GithubPullRequestState> states) {
        return states.isEmpty() || states.size() == GithubPullRequestState.values().length;
    }

    private static Set<GithubPullRequestState> toSet(GithubPullRequestFilter filter) {
        return filter.states().isEmpty()
                ? EnumSet.noneOf(GithubPullRequestState.class)
                : EnumSet.copyOf(filter.states());
    }
}
