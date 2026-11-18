package com.korur.oba.connect.github.client;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 2. Third-party
import org.hamcrest.Matchers;

// 3. Java SDK
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.github.dto.GithubContributorPayload;
import com.korur.oba.connect.github.dto.GithubPullRequestFilter;
import com.korur.oba.connect.github.dto.GithubPullRequestPagePayload;
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.connect.github.dto.GithubPullRequestSummaryPayload;
import com.korur.oba.connect.github.dto.GithubRepositoryPayload;
import com.korur.oba.connect.github.enums.GithubPullRequestState;
import com.korur.oba.connect.github.exception.ExceptionDomainGithubError;
import com.korur.oba.connect.github.exception.GithubRateLimitedException;
import com.korur.oba.connect.github.mapper.GithubPayloadMapperImpl;
import com.korur.oba.connect.github.mock_data.GithubMocks;
import com.korur.oba.connect.github.properties.GithubProperties;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class GithubClientUnitTest {

    private static final String API = "https://api.github.com";
    private static final String FULL_NAME = "senseylabs/village";
    private static final String TOKEN = "ghs_installation";
    private static final String TOKEN_URL = API + "/app/installations/777/access_tokens";
    private static final String TOKEN_BODY = "{\"token\":\"" + TOKEN + "\",\"expires_at\":\"2026-10-28T12:00:00Z\"}";

    @Mock
    private LoggingService loggingService;

    private GithubMocks.MutableClock clock;
    private MockRestServiceServer server;
    private GithubProperties properties;
    private GithubClient client;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        clock = GithubMocks.clock();
        organizationId = UUID.randomUUID();
        properties = GithubMocks.properties(GithubMocks.privateKeyConfig(GithubMocks.generateKeyPair()),
                organizationId);
        RestClient.Builder builder = GithubMocks.restClientBuilder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        GithubAppTokenProvider tokenProvider =
                new GithubAppTokenProvider(restClient, properties, clock, loggingService);
        client = new GithubClient(restClient, tokenProvider, properties, new GithubPayloadMapperImpl(), clock,
                loggingService);
    }

    private void expectToken() {
        server.expect(requestTo(TOKEN_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));
    }

    private void expectGet(String url, String body) {
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static String pull(int number, String title, String state, boolean draft, String mergedAt) {
        return "{\"number\":" + number + ",\"title\":\"" + title + "\",\"state\":\"" + state + "\",\"draft\":" + draft
                + ",\"merged_at\":" + (mergedAt == null ? "null" : "\"" + mergedAt + "\"")
                + ",\"updated_at\":\"2026-10-20T08:00:00Z\",\"user\":{\"login\":\"octo\"},"
                + "\"html_url\":\"https://evil.example/x\","
                + "\"head\":{\"ref\":\"feat/KAI-12-thing\"},\"base\":{\"ref\":\"main\"}}";
    }

    private static String repositories(int total, String... repos) {
        return "{\"total_count\":" + total + ",\"repositories\":[" + String.join(",", repos) + "]}";
    }

    private static String repo(long id, String fullName) {
        return "{\"id\":" + id + ",\"full_name\":\"" + fullName + "\",\"private\":true}";
    }

    @Nested
    class Gate {

        @Test
        void isEnabledFor_configuredOrganization_returnsTrue() {
            assertThat(client.isEnabledFor(organizationId)).isTrue();
        }

        @Test
        void isEnabledFor_otherOrganization_returnsFalse() {
            assertThat(client.isEnabledFor(UUID.randomUUID())).isFalse();
        }

        @Test
        void isEnabledFor_blankKey_returnsFalse() {
            properties.setAppId("");
            assertThat(client.isEnabledFor(organizationId)).isFalse();
        }

        @Test
        void callsWhileNotConfigured_throwNotConfiguredWithoutRequest() {
            // Arrange
            properties.setInstallationId(null);

            // Act and assert
            List<Runnable> calls = List.of(
                    () -> client.listRepositories(),
                    () -> client.findRepository(1L),
                    () -> client.listPullRequests(FULL_NAME, null, GithubPullRequestFilter.none(), 1),
                    () -> client.listContributors(FULL_NAME),
                    () -> client.getPullRequest(FULL_NAME, 1));
            for (Runnable call : calls) {
                InternalServerErrorException exception = assertThrows(InternalServerErrorException.class, call::run);
                assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainGithubError.INT_GITHUB_NOT_CONFIGURED.getCode());
            }
            server.verify();
        }
    }

    @Nested
    class Repositories {

        @Test
        void listRepositories_unsortedMixedCase_returnsSortedIgnoringCase() {
            // Arrange
            expectToken();
            expectGet(API + "/installation/repositories?per_page=100&page=1",
                    repositories(3, repo(3, "senseylabs/zeta"), repo(1, "senseylabs/Alpha"),
                            repo(2, "senseylabs/beta")));

            // Act
            List<GithubRepositoryPayload> result = client.listRepositories();

            // Assert
            assertThat(result).extracting(GithubRepositoryPayload::fullName)
                    .containsExactly("senseylabs/Alpha", "senseylabs/beta", "senseylabs/zeta");
            assertThat(result.get(0).githubRepositoryId()).isEqualTo(1L);
            assertThat(result.get(0).privateRepository()).isTrue();
        }

        @Test
        void listRepositories_calledTwiceWithinFiveMinutes_callsGitHubOnce() {
            // Arrange
            expectToken();
            expectGet(API + "/installation/repositories?per_page=100&page=1", repositories(1, repo(1, "a/b")));
            client.listRepositories();
            clock.advance(Duration.ofMinutes(4));

            // Act
            List<GithubRepositoryPayload> second = client.listRepositories();

            // Assert
            assertThat(second).hasSize(1);
            server.verify();
        }

        @Test
        void listRepositories_cacheExpired_loadsAgain() {
            // Arrange
            expectToken();
            expectGet(API + "/installation/repositories?per_page=100&page=1", repositories(1, repo(1, "a/b")));
            expectGet(API + "/installation/repositories?per_page=100&page=1",
                    repositories(2, repo(1, "a/b"), repo(2, "a/c")));
            client.listRepositories();
            clock.advance(Duration.ofMinutes(5).plusSeconds(1));

            // Act
            List<GithubRepositoryPayload> second = client.listRepositories();

            // Assert
            assertThat(second).hasSize(2);
            server.verify();
        }

        @Test
        void listRepositories_fullFirstPage_readsNextPage() {
            // Arrange
            String[] first = new String[100];
            for (int index = 0; index < 100; index++) {
                first[index] = repo(index + 1L, "o/r" + index);
            }
            expectToken();
            expectGet(API + "/installation/repositories?per_page=100&page=1", repositories(101, first));
            expectGet(API + "/installation/repositories?per_page=100&page=2", repositories(101, repo(500, "o/last")));

            // Act
            List<GithubRepositoryPayload> result = client.listRepositories();

            // Assert
            assertThat(result).hasSize(101);
            server.verify();
        }

        @Test
        void findRepository_idOutsideInstallation_returnsEmpty() {
            // Arrange
            expectToken();
            expectGet(API + "/installation/repositories?per_page=100&page=1", repositories(1, repo(1, "a/b")));

            // Act
            Optional<GithubRepositoryPayload> result = client.findRepository(999L);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void findRepository_idInInstallation_returnsRepository() {
            // Arrange
            expectToken();
            expectGet(API + "/installation/repositories?per_page=100&page=1", repositories(1, repo(7, "a/b")));

            // Act
            Optional<GithubRepositoryPayload> result = client.findRepository(7L);

            // Assert
            assertThat(result).map(GithubRepositoryPayload::fullName).contains("a/b");
        }
    }

    @Nested
    class PullRequestList {

        private static final String LIST_URL = API
                + "/repos/senseylabs/village/pulls?state=all&sort=updated&direction=desc&per_page=30&page=1";

        @Test
        void listPullRequests_noSearch_mapsEveryStateAndBuildsUrlFromName() {
            // Arrange
            expectToken();
            expectGet(LIST_URL, "[" + pull(1, "merged one", "closed", false, "2026-10-01T00:00:00Z") + ","
                    + pull(2, "draft one", "open", true, null) + ","
                    + pull(3, "closed one", "closed", false, null) + ","
                    + pull(4, "open one", "open", false, null) + "]");

            // Act
            GithubPullRequestPagePayload page = client.listPullRequests(FULL_NAME, null, GithubPullRequestFilter.none(), 1);

            // Assert
            assertThat(page.items()).extracting(GithubPullRequestSummaryPayload::state).containsExactly(
                    GithubPullRequestState.MERGED, GithubPullRequestState.DRAFT, GithubPullRequestState.CLOSED,
                    GithubPullRequestState.OPEN);
            assertThat(page.items().get(0).url()).isEqualTo("https://github.com/senseylabs/village/pull/1");
            assertThat(page.items().get(0).authorLogin()).isEqualTo("octo");
            assertThat(page.page()).isEqualTo(1);
            assertThat(page.hasMore()).isFalse();
        }

        @Test
        void listPullRequests_fullPage_reportsHasMore() {
            // Arrange
            StringBuilder body = new StringBuilder("[");
            for (int index = 1; index <= 30; index++) {
                body.append(index > 1 ? "," : "").append(pull(index, "t" + index, "open", false, null));
            }
            body.append("]");
            expectToken();
            expectGet(LIST_URL, body.toString());

            // Act
            GithubPullRequestPagePayload page = client.listPullRequests(FULL_NAME, "  ", GithubPullRequestFilter.none(), 1);

            // Assert
            assertThat(page.items()).hasSize(30);
            assertThat(page.hasMore()).isTrue();
        }

        @Test
        void listPullRequests_onlyQualifierCharacters_listsEverything() {
            // Arrange
            expectToken();
            expectGet(LIST_URL, "[]");

            // Act
            GithubPullRequestPagePayload page = client.listPullRequests(FULL_NAME, " : : ", GithubPullRequestFilter.none(), 1);

            // Assert
            assertThat(page.items()).isEmpty();
            server.verify();
        }

        @Test
        void listPullRequests_hashNumberSearch_readsThatPullRequest() {
            // Arrange
            expectToken();
            expectGet(API + "/repos/senseylabs/village/pulls/379", pull(379, "found", "open", false, null));

            // Act
            GithubPullRequestPagePayload page = client.listPullRequests(FULL_NAME, "#379", GithubPullRequestFilter.none(), 1);

            // Assert
            assertThat(page.items()).hasSize(1);
            assertThat(page.items().get(0).number()).isEqualTo(379);
            assertThat(page.hasMore()).isFalse();
        }

        @Test
        void listPullRequests_plainNumberSearchUnknown_returnsEmptyPage() {
            // Arrange
            expectToken();
            server.expect(requestTo(API + "/repos/senseylabs/village/pulls/12"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));

            // Act
            GithubPullRequestPagePayload page = client.listPullRequests(FULL_NAME, "12", GithubPullRequestFilter.none(), 1);

            // Assert
            assertThat(page.items()).isEmpty();
            assertThat(page.hasMore()).isFalse();
        }

        @Test
        void listPullRequests_numberSearchOnLaterPage_makesNoCall() {
            // Arrange
            server.expect(never(), requestTo(Matchers.any(String.class)));

            // Act
            GithubPullRequestPagePayload page = client.listPullRequests(FULL_NAME, "#379", GithubPullRequestFilter.none(), 2);

            // Assert
            assertThat(page.items()).isEmpty();
            assertThat(page.page()).isEqualTo(2);
            assertThat(page.hasMore()).isFalse();
            server.verify();
        }

        @Test
        void listPullRequests_textSearch_stripsColonSoNoQualifierCanBeAdded() {
            // Arrange
            expectToken();
            expectGet(API + "/search/issues?q=repo%3Asenseylabs%2Fvillage%20is%3Apr%20fix%20repo%20other%2Fx"
                    + "&sort=updated&order=desc&per_page=30&page=1",
                    "{\"total_count\":1,\"items\":[{\"number\":5,\"title\":\"fix it\",\"state\":\"closed\","
                            + "\"draft\":false,\"updated_at\":\"2026-10-20T08:00:00Z\","
                            + "\"user\":{\"login\":\"octo\"},\"pull_request\":{\"merged_at\":\"2026-10-02T00:00:00Z\"}}]}");

            // Act
            GithubPullRequestPagePayload page = client.listPullRequests(FULL_NAME, "fix  repo:other/x", GithubPullRequestFilter.none(), 1);

            // Assert
            assertThat(page.items()).hasSize(1);
            assertThat(page.items().get(0).state()).isEqualTo(GithubPullRequestState.MERGED);
            assertThat(page.items().get(0).url()).isEqualTo("https://github.com/senseylabs/village/pull/5");
            assertThat(page.hasMore()).isFalse();
            server.verify();
        }

        @Test
        void listPullRequests_searchFullPageWithMoreResults_reportsHasMore() {
            // Arrange
            StringBuilder items = new StringBuilder();
            for (int index = 1; index <= 30; index++) {
                items.append(index > 1 ? "," : "").append("{\"number\":").append(index)
                        .append(",\"title\":\"t\",\"state\":\"open\",\"draft\":false,")
                        .append("\"user\":{\"login\":\"o\"},\"pull_request\":{}}");
            }
            expectToken();
            server.expect(requestTo(Matchers.startsWith(API + "/search/issues")))
                    .andRespond(withSuccess("{\"total_count\":45,\"items\":[" + items + "]}",
                            MediaType.APPLICATION_JSON));

            // Act
            GithubPullRequestPagePayload page = client.listPullRequests(FULL_NAME, "text", GithubPullRequestFilter.none(), 1);

            // Assert
            assertThat(page.items()).hasSize(30);
            assertThat(page.hasMore()).isTrue();
        }

        @Test
        void listPullRequests_searchLastReachablePage_reportsNoMore() {
            // Arrange
            StringBuilder items = new StringBuilder();
            for (int index = 1; index <= 30; index++) {
                items.append(index > 1 ? "," : "").append("{\"number\":").append(index)
                        .append(",\"title\":\"t\",\"state\":\"open\",\"draft\":false,")
                        .append("\"user\":{\"login\":\"o\"},\"pull_request\":{}}");
            }
            expectToken();
            server.expect(requestTo(Matchers.startsWith(API + "/search/issues")))
                    .andRespond(withSuccess("{\"total_count\":60,\"items\":[" + items + "]}",
                            MediaType.APPLICATION_JSON));

            // Act
            GithubPullRequestPagePayload page = client.listPullRequests(FULL_NAME, "text", GithubPullRequestFilter.none(), 2);

            // Assert
            assertThat(page.hasMore()).isFalse();
        }

        @Test
        void listPullRequests_malformedFullName_throwsUnexpected() {
            // Arrange
            expectToken();

            // Act and assert
            assertThrows(InternalServerErrorException.class, () -> client.listPullRequests("no-slash", null, GithubPullRequestFilter.none(), 1));
        }
    }

    @Nested
    class SinglePullRequest {

        @Test
        void getPullRequest_exists_returnsBranchesAndBuiltUrl() {
            // Arrange
            expectToken();
            expectGet(API + "/repos/senseylabs/village/pulls/688", pull(688, "feat: x", "open", false, null));

            // Act
            Optional<GithubPullRequestPayload> result = client.getPullRequest(FULL_NAME, 688);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().headRef()).isEqualTo("feat/KAI-12-thing");
            assertThat(result.get().baseRef()).isEqualTo("main");
            assertThat(result.get().url()).isEqualTo("https://github.com/senseylabs/village/pull/688");
            assertThat(result.get().state()).isEqualTo(GithubPullRequestState.OPEN);
        }

        @Test
        void getPullRequest_gitHubAnswers404_returnsEmpty() {
            // Arrange
            expectToken();
            server.expect(requestTo(API + "/repos/senseylabs/village/pulls/1"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));

            // Act
            Optional<GithubPullRequestPayload> result = client.getPullRequest(FULL_NAME, 1);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class Errors {

        private static final String PULL_URL = API + "/repos/senseylabs/village/pulls/1";

        private ApplicationException failWith(HttpStatus status, HttpHeaders headers) {
            expectToken();
            server.expect(requestTo(PULL_URL)).andRespond(withStatus(status).headers(headers));
            return assertThrows(ApplicationException.class, () -> client.getPullRequest(FULL_NAME, 1));
        }

        @Test
        void getPullRequest_unauthorizedThenAuthorized_refreshesTokenOnceAndSucceeds() {
            // Arrange
            expectToken();
            server.expect(once(), requestTo(PULL_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
            server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(
                    "{\"token\":\"ghs_second\",\"expires_at\":\"2026-10-28T12:00:00Z\"}", MediaType.APPLICATION_JSON));
            server.expect(requestTo(PULL_URL))
                    .andExpect(header("Authorization", "Bearer ghs_second"))
                    .andRespond(withSuccess(pull(1, "t", "open", false, null), MediaType.APPLICATION_JSON));

            // Act
            Optional<GithubPullRequestPayload> result = client.getPullRequest(FULL_NAME, 1);

            // Assert
            assertThat(result).isPresent();
            server.verify();
        }

        @Test
        void getPullRequest_unauthorizedTwice_throwsCredentialsRefused() {
            // Arrange
            expectToken();
            server.expect(requestTo(PULL_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
            expectToken();
            server.expect(requestTo(PULL_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

            // Act
            ApplicationException exception =
                    assertThrows(ApplicationException.class, () -> client.getPullRequest(FULL_NAME, 1));

            // Assert
            assertThat(exception).isInstanceOf(BadGatewayException.class);
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_CREDENTIALS_REFUSED.getCode());
            server.verify();
        }

        @Test
        void getPullRequest_forbiddenWithoutRateLimitHeaders_throwsCredentialsRefused() {
            // Act
            ApplicationException exception = failWith(HttpStatus.FORBIDDEN, new HttpHeaders());

            // Assert
            assertThat(exception).isInstanceOf(BadGatewayException.class);
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_CREDENTIALS_REFUSED.getCode());
        }

        @Test
        void getPullRequest_forbiddenWithExhaustedRemaining_throwsRateLimited503() {
            // Arrange
            HttpHeaders headers = new HttpHeaders();
            headers.add("x-ratelimit-remaining", "0");

            // Act
            ApplicationException exception = failWith(HttpStatus.FORBIDDEN, headers);

            // Assert
            assertThat(exception).isInstanceOf(GithubRateLimitedException.class);
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_RATE_LIMITED.getCode());
        }

        @Test
        void getPullRequest_forbiddenWithRetryAfter_throwsRateLimited() {
            // Arrange
            HttpHeaders headers = new HttpHeaders();
            headers.add("retry-after", "60");

            // Act
            ApplicationException exception = failWith(HttpStatus.FORBIDDEN, headers);

            // Assert
            assertThat(exception).isInstanceOf(GithubRateLimitedException.class);
        }

        @Test
        void getPullRequest_forbiddenWithRemainingLeft_throwsCredentialsRefused() {
            // Arrange
            HttpHeaders headers = new HttpHeaders();
            headers.add("x-ratelimit-remaining", "12");

            // Act
            ApplicationException exception = failWith(HttpStatus.FORBIDDEN, headers);

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_CREDENTIALS_REFUSED.getCode());
        }

        @Test
        void getPullRequest_tooManyRequests_throwsRateLimited() {
            // Act
            ApplicationException exception = failWith(HttpStatus.TOO_MANY_REQUESTS, new HttpHeaders());

            // Assert
            assertThat(exception).isInstanceOf(GithubRateLimitedException.class);
        }

        @Test
        void getPullRequest_serverError_throwsUnreachable() {
            // Act
            ApplicationException exception = failWith(HttpStatus.BAD_GATEWAY, new HttpHeaders());

            // Assert
            assertThat(exception).isInstanceOf(BadGatewayException.class);
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_UNREACHABLE.getCode());
        }

        @Test
        void getPullRequest_unprocessableEntity_throwsUnreachable() {
            // Act
            ApplicationException exception = failWith(HttpStatus.UNPROCESSABLE_ENTITY, new HttpHeaders());

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_UNREACHABLE.getCode());
        }

        @Test
        void getPullRequest_connectionFails_throwsUnreachable() {
            // Arrange
            expectToken();
            server.expect(requestTo(PULL_URL)).andRespond(request -> {
                throw new IOException("read timed out");
            });

            // Act
            ApplicationException exception =
                    assertThrows(ApplicationException.class, () -> client.getPullRequest(FULL_NAME, 1));

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_UNREACHABLE.getCode());
        }
    }

    @Nested
    class FilteredList {

        private static final String PULLS = API + "/repos/senseylabs/village/pulls?state=%s"
                + "&sort=updated&direction=desc&per_page=30&page=1";

        private GithubPullRequestFilter filter(List<GithubPullRequestState> states, String... authors) {
            return new GithubPullRequestFilter(states, List.of(authors));
        }

        /**
         * Expects one search call and checks its decoded q and whether advanced_search is sent.
         */
        private void expectSearch(String expectedQuery, boolean advanced, String body) {
            server.expect(requestTo(Matchers.startsWith(API + "/search/issues?")))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(request -> {
                        URI uri = request.getURI();
                        MultiValueMap<String, String> params =
                                UriComponentsBuilder.fromUri(uri).build().getQueryParams();
                        assertThat(UriUtils.decode(params.getFirst("q"), StandardCharsets.UTF_8))
                                .isEqualTo(expectedQuery);
                        assertThat(params.getFirst("advanced_search")).isEqualTo(advanced ? "true" : null);
                        assertThat(params.getFirst("per_page")).isEqualTo("30");
                    })
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        }

        private String searchBody(int total) {
            return "{\"total_count\":" + total + ",\"items\":[]}";
        }

        @Test
        void openAndDraft_useThePullRequestListWithStateOpen() {
            // Arrange
            expectToken();
            expectGet(String.format(PULLS, "open"), "[]");

            // Act
            GithubPullRequestPagePayload page = client.listPullRequests(FULL_NAME, null,
                    filter(List.of(GithubPullRequestState.OPEN, GithubPullRequestState.DRAFT)), 1);

            // Assert
            assertThat(page.resultsLimited()).isFalse();
            server.verify();
        }

        @Test
        void mergedAndClosed_useThePullRequestListWithStateClosed() {
            // Arrange
            expectToken();
            expectGet(String.format(PULLS, "closed"), "[]");

            // Act
            client.listPullRequests(FULL_NAME, null,
                    filter(List.of(GithubPullRequestState.MERGED, GithubPullRequestState.CLOSED)), 1);

            // Assert
            server.verify();
        }

        @Test
        void allFourStates_useThePullRequestListWithStateAll() {
            // Arrange
            expectToken();
            expectGet(String.format(PULLS, "all"), "[]");

            // Act
            client.listPullRequests(FULL_NAME, null, filter(List.of(GithubPullRequestState.values())), 1);

            // Assert
            server.verify();
        }

        @Test
        void singleState_searchesWithoutAdvancedSearch() {
            // Arrange
            expectToken();
            expectSearch("repo:senseylabs/village is:pr is:merged", false, searchBody(0));

            // Act
            client.listPullRequests(FULL_NAME, null, filter(List.of(GithubPullRequestState.MERGED)), 1);

            // Assert
            server.verify();
        }

        @Test
        void oneAuthor_searchesEvenWithoutTextAndWithoutAdvancedSearch() {
            // Arrange
            expectToken();
            expectSearch("repo:senseylabs/village is:pr author:octo", false, searchBody(0));

            // Act
            client.listPullRequests(FULL_NAME, null, filter(List.of(), "octo"), 1);

            // Assert
            server.verify();
        }

        @Test
        void botAuthor_isSearchedAsAnApp() {
            // Arrange
            expectToken();
            expectSearch("repo:senseylabs/village is:pr author:app/dependabot", false, searchBody(0));

            // Act
            client.listPullRequests(FULL_NAME, null, filter(List.of(), "dependabot[bot]"), 1);

            // Assert
            server.verify();
        }

        @Test
        void severalAuthors_sendAdvancedSearch() {
            // Arrange
            expectToken();
            expectSearch("repo:senseylabs/village is:pr (author:app/dependabot OR author:octo)", true,
                    searchBody(0));

            // Act
            client.listPullRequests(FULL_NAME, null, filter(List.of(), "dependabot[bot]", "octo"), 1);

            // Assert
            server.verify();
        }

        @Test
        void mixedStatesWithText_sendAdvancedSearchAndCleanTheText() {
            // Arrange
            expectToken();
            expectSearch("repo:senseylabs/village is:pr ((is:open draft:false) OR is:merged)"
                    + " fix a b c or-gate foo bar", true, searchBody(0));

            // Act
            client.listPullRequests(FULL_NAME, "fix (a) \"b\" OR not c or-gate foo:bar",
                    filter(List.of(GithubPullRequestState.OPEN, GithubPullRequestState.MERGED)), 1);

            // Assert
            server.verify();
        }

        @Test
        void textWithoutOr_keepsParenthesesQuotesAndWordsButStripsColon() {
            // Arrange
            expectToken();
            expectSearch("repo:senseylabs/village is:pr is:open draft:false fix (a) \"b\" OR not c foo bar", false,
                    searchBody(0));

            // Act
            client.listPullRequests(FULL_NAME, "fix (a) \"b\" OR not c foo:bar",
                    filter(List.of(GithubPullRequestState.OPEN)), 1);

            // Assert
            server.verify();
        }

        @Test
        void numberSearch_ignoresTheFilter() {
            // Arrange
            expectToken();
            expectGet(API + "/repos/senseylabs/village/pulls/379", pull(379, "found", "closed", false, null));

            // Act
            GithubPullRequestPagePayload page = client.listPullRequests(FULL_NAME, "#379",
                    filter(List.of(GithubPullRequestState.OPEN), "octo"), 1);

            // Assert
            assertThat(page.items()).hasSize(1);
            assertThat(page.resultsLimited()).isFalse();
            server.verify();
        }

        @Test
        void totalOf1000_isNotLimited() {
            // Arrange
            expectToken();
            expectSearch("repo:senseylabs/village is:pr author:octo", false, searchBody(1000));

            // Act
            GithubPullRequestPagePayload page = client.listPullRequests(FULL_NAME, null, filter(List.of(), "octo"), 1);

            // Assert
            assertThat(page.resultsLimited()).isFalse();
        }

        @Test
        void totalOf1001_isLimited() {
            // Arrange
            expectToken();
            expectSearch("repo:senseylabs/village is:pr author:octo", false, searchBody(1001));

            // Act
            GithubPullRequestPagePayload page = client.listPullRequests(FULL_NAME, null, filter(List.of(), "octo"), 1);

            // Assert
            assertThat(page.resultsLimited()).isTrue();
        }

        @Test
        void unprocessableSearchWithAuthors_isAnEmptyPage() {
            // Arrange
            expectToken();
            server.expect(requestTo(Matchers.startsWith(API + "/search/issues?")))
                    .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

            // Act
            GithubPullRequestPagePayload page =
                    client.listPullRequests(FULL_NAME, null, filter(List.of(), "ghost-user"), 1);

            // Assert
            assertThat(page.items()).isEmpty();
            assertThat(page.hasMore()).isFalse();
            assertThat(page.resultsLimited()).isFalse();
            server.verify();
        }

        @Test
        void unprocessableSearchWithoutAuthors_throwsUnreachable() {
            // Arrange
            expectToken();
            server.expect(requestTo(Matchers.startsWith(API + "/search/issues?")))
                    .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

            // Act
            ApplicationException exception = assertThrows(ApplicationException.class,
                    () -> client.listPullRequests(FULL_NAME, "text", GithubPullRequestFilter.none(), 1));

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_UNREACHABLE.getCode());
        }
    }

    @Nested
    class RateLimitBody {

        private static final String PULL_URL = API + "/repos/senseylabs/village/pulls/1";

        private ApplicationException failForbiddenWith(String body) {
            expectToken();
            server.expect(requestTo(PULL_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON).body(body));
            return assertThrows(ApplicationException.class, () -> client.getPullRequest(FULL_NAME, 1));
        }

        @Test
        void forbiddenNamingTheRateLimitWithoutHeaders_throwsRateLimited() {
            // Act
            ApplicationException exception = failForbiddenWith(
                    "{\"message\":\"You have exceeded a secondary Rate Limit. Please wait.\"}");

            // Assert
            assertThat(exception).isInstanceOf(GithubRateLimitedException.class);
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_RATE_LIMITED.getCode());
        }

        @Test
        void plainForbiddenBody_staysCredentialsRefused() {
            // Act
            ApplicationException exception = failForbiddenWith("{\"message\":\"Resource not accessible\"}");

            // Assert
            assertThat(exception).isInstanceOf(BadGatewayException.class);
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_CREDENTIALS_REFUSED.getCode());
        }
    }

    @Nested
    class Contributors {

        private static final String CONTRIBUTORS_URL = API + "/repos/senseylabs/village/contributors?per_page=100";
        private static final String OTHER_FULL_NAME = "senseylabs/other";
        private static final String OTHER_URL = API + "/repos/senseylabs/other/contributors?per_page=100";

        private static String user(String login) {
            return "{\"login\":" + (login == null ? "null" : "\"" + login + "\"")
                    + ",\"email\":\"private@example.com\",\"name\":\"Private Name\"}";
        }

        @Test
        void listContributors_unsortedMixedCase_returnsSortedIgnoringCaseWithPerPageOnly() {
            // Arrange
            expectToken();
            expectGet(CONTRIBUTORS_URL, "[" + String.join(",", user("zeta"), user("Alpha"), user("beta")) + "]");

            // Act
            List<GithubContributorPayload> result = client.listContributors(FULL_NAME);

            // Assert
            assertThat(result).extracting(GithubContributorPayload::login).containsExactly("Alpha", "beta", "zeta");
            server.verify();
        }

        @Test
        void listContributors_nullAndInvalidLogins_areDroppedAndBotsStay() {
            // Arrange
            expectToken();
            expectGet(CONTRIBUTORS_URL, "[" + String.join(",", user(null), user("bad login"), user("a:b"),
                    user("-leading"), user("github-actions[bot]"), user("octo")) + "]");

            // Act
            List<GithubContributorPayload> result = client.listContributors(FULL_NAME);

            // Assert
            assertThat(result).extracting(GithubContributorPayload::login)
                    .containsExactly("github-actions[bot]", "octo");
        }

        @Test
        void listContributors_calledTwiceWithinTheHour_callsGitHubOnce() {
            // Arrange
            expectToken();
            expectGet(CONTRIBUTORS_URL, "[" + user("octo") + "]");
            client.listContributors(FULL_NAME);
            clock.advance(Duration.ofMinutes(59));

            // Act
            List<GithubContributorPayload> second = client.listContributors(FULL_NAME);

            // Assert
            assertThat(second).hasSize(1);
            server.verify();
        }

        @Test
        void listContributors_cacheExpired_loadsAgain() {
            // Arrange
            expectToken();
            expectGet(CONTRIBUTORS_URL, "[" + user("octo") + "]");
            expectGet(CONTRIBUTORS_URL, "[" + user("octo") + "," + user("ada") + "]");
            client.listContributors(FULL_NAME);
            clock.advance(Duration.ofHours(1).plusSeconds(1));

            // Act
            List<GithubContributorPayload> second = client.listContributors(FULL_NAME);

            // Assert
            assertThat(second).hasSize(2);
            server.verify();
        }

        @Test
        void listContributors_twoRepositories_doNotShareAnEntry() {
            // Arrange
            expectToken();
            expectGet(CONTRIBUTORS_URL, "[" + user("octo") + "]");
            expectGet(OTHER_URL, "[" + user("ada") + "]");

            // Act
            List<GithubContributorPayload> first = client.listContributors(FULL_NAME);
            List<GithubContributorPayload> other = client.listContributors(OTHER_FULL_NAME);

            // Assert
            assertThat(first).extracting(GithubContributorPayload::login).containsExactly("octo");
            assertThat(other).extracting(GithubContributorPayload::login).containsExactly("ada");
            server.verify();
        }

        @Test
        void listContributors_noContent_isEmptyAndCached() {
            // Arrange
            expectToken();
            server.expect(requestTo(CONTRIBUTORS_URL)).andRespond(withNoContent());

            // Act
            List<GithubContributorPayload> first = client.listContributors(FULL_NAME);
            List<GithubContributorPayload> second = client.listContributors(FULL_NAME);

            // Assert
            assertThat(first).isEmpty();
            assertThat(second).isEmpty();
            server.verify();
        }

        @Test
        void listContributors_serverError_throwsUnreachableAndIsNotCached() {
            // Arrange
            expectToken();
            server.expect(requestTo(CONTRIBUTORS_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
            expectGet(CONTRIBUTORS_URL, "[" + user("octo") + "]");

            // Act
            ApplicationException exception =
                    assertThrows(ApplicationException.class, () -> client.listContributors(FULL_NAME));
            List<GithubContributorPayload> retry = client.listContributors(FULL_NAME);

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_UNREACHABLE.getCode());
            assertThat(retry).hasSize(1);
            server.verify();
        }

        @Test
        void listContributors_plainForbidden_throwsCredentialsRefusedAndIsNotCached() {
            // Arrange
            expectToken();
            server.expect(requestTo(CONTRIBUTORS_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
            expectGet(CONTRIBUTORS_URL, "[]");

            // Act
            ApplicationException exception =
                    assertThrows(ApplicationException.class, () -> client.listContributors(FULL_NAME));
            List<GithubContributorPayload> retry = client.listContributors(FULL_NAME);

            // Assert
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_CREDENTIALS_REFUSED.getCode());
            assertThat(retry).isEmpty();
            server.verify();
        }

        @Test
        void listContributors_rateLimitedForbidden_throwsRateLimited() {
            // Arrange
            HttpHeaders headers = new HttpHeaders();
            headers.add("x-ratelimit-remaining", "0");
            expectToken();
            server.expect(requestTo(CONTRIBUTORS_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

            // Act
            ApplicationException exception =
                    assertThrows(ApplicationException.class, () -> client.listContributors(FULL_NAME));

            // Assert
            assertThat(exception).isInstanceOf(GithubRateLimitedException.class);
            assertThat(exception.getErrorCode())
                    .isEqualTo(ExceptionDomainGithubError.UPS_GITHUB_RATE_LIMITED.getCode());
        }
    }
}
