package com.korur.oba.connect.github.mapper;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.time.Instant;
import java.util.List;

// 4. Internal
import com.korur.oba.connect.github.dto.GithubContributorPayload;
import com.korur.oba.connect.github.dto.GithubPullRequestPagePayload;
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.connect.github.dto.GithubPullRequestSummaryPayload;
import com.korur.oba.connect.github.dto.GithubRepositoryPayload;
import com.korur.oba.connect.github.dto.api.GithubApiBranchRef;
import com.korur.oba.connect.github.dto.api.GithubApiIssue;
import com.korur.oba.connect.github.dto.api.GithubApiIssuePullRequest;
import com.korur.oba.connect.github.dto.api.GithubApiPullRequest;
import com.korur.oba.connect.github.dto.api.GithubApiRepository;
import com.korur.oba.connect.github.dto.api.GithubApiUser;
import com.korur.oba.connect.github.enums.GithubPullRequestState;

class GithubPayloadMapperUnitTest {

    private static final String FULL_NAME = "senseylabs/fabrikam";
    private static final Instant UPDATED_AT = Instant.parse("2026-10-27T08:00:00Z");
    private static final Instant MERGED_AT = Instant.parse("2026-10-27T09:30:00Z");

    private final GithubPayloadMapper mapper = new GithubPayloadMapperImpl();

    private GithubApiPullRequest apiPullRequest(String state, boolean draft, Instant mergedAt) {
        return new GithubApiPullRequest(379, "Mobile menu", state, draft, mergedAt, UPDATED_AT,
                new GithubApiUser("ada-dev"), new GithubApiBranchRef("feature/ACME-241-mobile-menu"),
                new GithubApiBranchRef("main"));
    }

    private GithubApiIssue apiIssue(String state, boolean draft, GithubApiIssuePullRequest pullRequest) {
        return new GithubApiIssue(379, "Mobile menu", state, draft, UPDATED_AT, new GithubApiUser("ada-dev"),
                pullRequest);
    }

    @Nested
    class ToRepositoryPayload {

        @Test
        void repository_mapsIdNameAndPrivacy() {
            // Arrange
            GithubApiRepository repository = new GithubApiRepository(812345678L, FULL_NAME, true);

            // Act
            GithubRepositoryPayload result = mapper.toRepositoryPayload(repository);

            // Assert
            assertThat(result).isEqualTo(new GithubRepositoryPayload(812345678L, FULL_NAME, true));
        }

        @Test
        void repositories_mapEveryEntryInOrder() {
            // Arrange
            List<GithubApiRepository> repositories = List.of(new GithubApiRepository(2L, "senseylabs/b", false),
                    new GithubApiRepository(1L, "senseylabs/a", true));

            // Act
            List<GithubRepositoryPayload> result = mapper.toRepositoryPayloads(repositories);

            // Assert
            assertThat(result).extracting(GithubRepositoryPayload::githubRepositoryId).containsExactly(2L, 1L);
        }
    }

    @Nested
    class ToSummaryPayload {

        @Test
        void listedPullRequest_buildsTheLinkFromTheRepositoryNameAndTheNumber() {
            // Arrange
            GithubApiPullRequest pullRequest = apiPullRequest("open", false, null);

            // Act
            GithubPullRequestSummaryPayload result = mapper.toSummaryPayload(pullRequest, FULL_NAME);

            // Assert
            assertThat(result.number()).isEqualTo(379);
            assertThat(result.title()).isEqualTo("Mobile menu");
            assertThat(result.state()).isEqualTo(GithubPullRequestState.OPEN);
            assertThat(result.authorLogin()).isEqualTo("ada-dev");
            assertThat(result.updatedAt()).isEqualTo(UPDATED_AT);
            assertThat(result.url()).isEqualTo("https://github.com/senseylabs/fabrikam/pull/379");
        }

        @Test
        void searchHit_mapsTheIssueFieldsAndTheMergeTimeOfItsPullRequestPart() {
            // Arrange
            GithubApiIssue issue = apiIssue("closed", false, new GithubApiIssuePullRequest(MERGED_AT));

            // Act
            GithubPullRequestSummaryPayload result = mapper.toSummaryPayload(issue, FULL_NAME);

            // Assert
            assertThat(result.state()).isEqualTo(GithubPullRequestState.MERGED);
            assertThat(result.authorLogin()).isEqualTo("ada-dev");
            assertThat(result.url()).isEqualTo("https://github.com/senseylabs/fabrikam/pull/379");
        }

        @Test
        void fullPullRequest_becomesAPickerRowWithTheSameFacts() {
            // Arrange
            GithubPullRequestPayload pullRequest = new GithubPullRequestPayload(379, "Mobile menu",
                    GithubPullRequestState.CLOSED, "ada-dev", UPDATED_AT,
                    "https://github.com/senseylabs/fabrikam/pull/379", "feature/x", "main");

            // Act
            GithubPullRequestSummaryPayload result = mapper.toSummaryPayload(pullRequest);

            // Assert
            assertThat(result).isEqualTo(new GithubPullRequestSummaryPayload(379, "Mobile menu",
                    GithubPullRequestState.CLOSED, "ada-dev", UPDATED_AT,
                    "https://github.com/senseylabs/fabrikam/pull/379"));
        }
    }

    @Nested
    class ToPullRequestPayload {

        @Test
        void detailPullRequest_carriesTheBranchesAndTheBuiltLink() {
            // Arrange
            GithubApiPullRequest pullRequest = apiPullRequest("open", true, null);

            // Act
            GithubPullRequestPayload result = mapper.toPullRequestPayload(pullRequest, FULL_NAME);

            // Assert
            assertThat(result.state()).isEqualTo(GithubPullRequestState.DRAFT);
            assertThat(result.headRef()).isEqualTo("feature/ACME-241-mobile-menu");
            assertThat(result.baseRef()).isEqualTo("main");
            assertThat(result.url()).isEqualTo("https://github.com/senseylabs/fabrikam/pull/379");
        }
    }

    @Nested
    class ToPagePayload {

        @Test
        void rows_keepOrderPageHasMoreAndResultsLimited() {
            // Arrange
            GithubPullRequestSummaryPayload row = new GithubPullRequestSummaryPayload(1, "t",
                    GithubPullRequestState.OPEN, "ada-dev", UPDATED_AT, "u");

            // Act
            GithubPullRequestPagePayload result = mapper.toPagePayload(List.of(row), 3, true, true);

            // Assert
            assertThat(result).isEqualTo(new GithubPullRequestPagePayload(List.of(row), 3, true, true));
            assertThat(result.resultsLimited()).isTrue();
        }

        @Test
        void notLimited_keepsResultsLimitedFalse() {
            // Act
            GithubPullRequestPagePayload result = mapper.toPagePayload(List.of(), 1, false, false);

            // Assert
            assertThat(result).isEqualTo(new GithubPullRequestPagePayload(List.of(), 1, false, false));
        }
    }

    @Nested
    class ToContributorPayload {

        @Test
        void user_carriesOnlyTheLogin() {
            // Arrange
            GithubApiUser user = new GithubApiUser("github-actions[bot]");

            // Act
            GithubContributorPayload result = mapper.toContributorPayload(user);

            // Assert
            assertThat(result).isEqualTo(new GithubContributorPayload("github-actions[bot]"));
        }
    }

    @Nested
    class ToState {

        @ParameterizedTest
        @CsvSource({
                "open,false,false,OPEN",
                "open,true,false,DRAFT",
                "closed,false,false,CLOSED",
                "closed,true,false,CLOSED",
                "open,false,true,MERGED",
                "closed,false,true,MERGED",
                "open,true,true,MERGED"
        })
        void stateDraftAndMergeTime_deriveThePickerState(String state, boolean draft, boolean merged,
                                                         GithubPullRequestState expected) {
            // Act
            GithubPullRequestState result = mapper.toState(state, draft, merged ? MERGED_AT : null);

            // Assert
            assertThat(result).isEqualTo(expected);
        }

        @Test
        void issueWithoutPullRequestPart_isNotMerged() {
            // Arrange
            GithubApiIssue issue = apiIssue("closed", false, null);

            // Act
            GithubPullRequestState result = mapper.toState(issue);

            // Assert
            assertThat(result).isEqualTo(GithubPullRequestState.CLOSED);
        }

        @Test
        void issueWithMergeTime_isMerged() {
            // Arrange
            GithubApiIssue issue = apiIssue("closed", false, new GithubApiIssuePullRequest(MERGED_AT));

            // Act
            GithubPullRequestState result = mapper.toState(issue);

            // Assert
            assertThat(result).isEqualTo(GithubPullRequestState.MERGED);
        }

        @Test
        void openDraftPullRequest_isADraft() {
            // Act
            GithubPullRequestState result = mapper.toState(apiPullRequest("open", true, null));

            // Assert
            assertThat(result).isEqualTo(GithubPullRequestState.DRAFT);
        }
    }

    @Nested
    class ToPullRequestUrl {

        @Test
        void numberAndRepository_buildAGithubComLink() {
            // Act
            String result = mapper.toPullRequestUrl(12, "senseylabs/village");

            // Assert
            assertThat(result).isEqualTo("https://github.com/senseylabs/village/pull/12");
        }
    }
}
