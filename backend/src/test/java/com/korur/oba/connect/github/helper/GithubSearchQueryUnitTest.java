package com.korur.oba.connect.github.helper;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// 4. Internal
import com.korur.oba.connect.github.dto.GithubPullRequestFilter;
import com.korur.oba.connect.github.enums.GithubPullRequestState;

class GithubSearchQueryUnitTest {

    private static final String FULL_NAME = "senseylabs/village";
    private static final String BASE = "repo:senseylabs/village is:pr";

    private static GithubPullRequestFilter states(String statesCsv) {
        return filter(statesCsv, new String[0]);
    }

    private static GithubPullRequestFilter filter(String statesCsv, String... authors) {
        List<GithubPullRequestState> states = statesCsv == null || statesCsv.isBlank()
                ? List.of()
                : Arrays.stream(statesCsv.split("\\+")).map(GithubPullRequestState::valueOf).toList();
        return new GithubPullRequestFilter(states, List.of(authors));
    }

    @Nested
    class ListState {

        @ParameterizedTest
        @CsvSource({
                "'',all",
                "OPEN+DRAFT+MERGED+CLOSED,all",
                "OPEN+DRAFT,open",
                "DRAFT+OPEN,open",
                "MERGED+CLOSED,closed"
        })
        void statesThatAListServes_giveItsStateValue(String statesCsv, String expected) {
            // Act
            Optional<String> result = GithubSearchQuery.listState(states(statesCsv));

            // Assert
            assertThat(result).contains(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "OPEN", "DRAFT", "MERGED", "CLOSED", "OPEN+MERGED", "OPEN+CLOSED", "DRAFT+MERGED", "DRAFT+CLOSED",
                "OPEN+DRAFT+MERGED", "OPEN+DRAFT+CLOSED", "OPEN+MERGED+CLOSED", "DRAFT+MERGED+CLOSED"
        })
        void statesOnlySearchServes_giveNoListState(String statesCsv) {
            // Act
            Optional<String> result = GithubSearchQuery.listState(states(statesCsv));

            // Assert
            assertThat(result).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "OPEN+DRAFT", "MERGED+CLOSED"})
        void anyAuthor_givesNoListState(String statesCsv) {
            // Act
            Optional<String> result = GithubSearchQuery.listState(filter(statesCsv, "octo"));

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class StateTerm {

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "''|''",
                "OPEN+DRAFT+MERGED+CLOSED|''",
                "OPEN|is:open draft:false",
                "DRAFT|is:open draft:true",
                "OPEN+DRAFT|is:open",
                "MERGED|is:merged",
                "CLOSED|is:closed is:unmerged",
                "MERGED+CLOSED|is:closed",
                "OPEN+MERGED|((is:open draft:false) OR is:merged)",
                "OPEN+CLOSED|((is:open draft:false) OR (is:closed is:unmerged))",
                "DRAFT+MERGED|((is:open draft:true) OR is:merged)",
                "DRAFT+CLOSED|((is:open draft:true) OR (is:closed is:unmerged))",
                "OPEN+DRAFT+MERGED|(is:open OR is:merged)",
                "OPEN+DRAFT+CLOSED|(is:open OR (is:closed is:unmerged))",
                "OPEN+MERGED+CLOSED|((is:open draft:false) OR is:closed)",
                "DRAFT+MERGED+CLOSED|((is:open draft:true) OR is:closed)"
        })
        void everyStateSet_buildsItsTerm(String statesCsv, String expectedTerm) {
            // Act
            String query = GithubSearchQuery.build(FULL_NAME, "", states(statesCsv));

            // Assert
            assertThat(query).isEqualTo(expectedTerm.isEmpty() ? BASE : BASE + " " + expectedTerm);
        }

        @ParameterizedTest
        @CsvSource({
                "OPEN,false", "DRAFT,false", "OPEN+DRAFT,false", "MERGED,false", "CLOSED,false",
                "MERGED+CLOSED,false", "'',false", "OPEN+DRAFT+MERGED+CLOSED,false",
                "OPEN+MERGED,true", "OPEN+CLOSED,true", "DRAFT+MERGED,true", "DRAFT+CLOSED,true",
                "OPEN+DRAFT+MERGED,true", "OPEN+DRAFT+CLOSED,true", "OPEN+MERGED+CLOSED,true",
                "DRAFT+MERGED+CLOSED,true"
        })
        void needsAdvancedSearch_onlyWhenTheQueryHoldsAnOr(String statesCsv, boolean expected) {
            // Act
            boolean result = GithubSearchQuery.needsAdvancedSearch(states(statesCsv));

            // Assert
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    class AuthorTerm {

        @Test
        void oneAuthor_isOneQualifierWithoutParentheses() {
            // Act
            String query = GithubSearchQuery.build(FULL_NAME, "", filter("", "octo"));

            // Assert
            assertThat(query).isEqualTo(BASE + " author:octo");
            assertThat(GithubSearchQuery.needsAdvancedSearch(filter("", "octo"))).isFalse();
        }

        @Test
        void severalAuthors_areGroupedAlternatives() {
            // Arrange
            GithubPullRequestFilter filter = filter("", "octo", "ada-dev");

            // Act
            String query = GithubSearchQuery.build(FULL_NAME, "", filter);

            // Assert
            assertThat(query).isEqualTo(BASE + " (author:octo OR author:ada-dev)");
            assertThat(GithubSearchQuery.needsAdvancedSearch(filter)).isTrue();
        }

        @Test
        void botLogin_isWrittenAsAnAppAuthorWithoutTheSuffix() {
            // Act
            String single = GithubSearchQuery.build(FULL_NAME, "", filter("", "dependabot[bot]"));
            String mixed = GithubSearchQuery.build(FULL_NAME, "", filter("", "dependabot[bot]", "octo"));

            // Assert
            assertThat(single).isEqualTo(BASE + " author:app/dependabot");
            assertThat(mixed).isEqualTo(BASE + " (author:app/dependabot OR author:octo)");
        }

        @Test
        void authorsThenStateThenText_keepThatOrder() {
            // Act
            String query = GithubSearchQuery.build(FULL_NAME, "menu", filter("OPEN", "octo"));

            // Assert
            assertThat(query).isEqualTo(BASE + " author:octo is:open draft:false menu");
        }

        @Test
        void fiveAuthorsAndAMixedState_holdFiveOrOperators() {
            // Arrange
            GithubPullRequestFilter filter = filter("OPEN+MERGED", "a", "b", "c", "d", "e");

            // Act
            String query = GithubSearchQuery.build(FULL_NAME, "", filter);

            // Assert
            assertThat(query).isEqualTo(BASE + " (author:a OR author:b OR author:c OR author:d OR author:e)"
                    + " ((is:open draft:false) OR is:merged)");
            assertThat(query.split(" OR ", -1)).hasSize(6);
        }
    }

    @Nested
    class Text {

        @Test
        void noFilterAndText_putsTheTextLast() {
            // Act
            String query = GithubSearchQuery.build(FULL_NAME, "fix menu", GithubPullRequestFilter.none());

            // Assert
            assertThat(query).isEqualTo(BASE + " fix menu");
        }

        @Test
        void noFilterAndNoText_isTheRepositoryAndPullRequestQualifiers() {
            // Act
            String query = GithubSearchQuery.build(FULL_NAME, "", GithubPullRequestFilter.none());

            // Assert
            assertThat(query).isEqualTo(BASE);
        }
    }
}
