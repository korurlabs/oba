package com.korur.oba.connect.github.client;

// 1. Framework-level
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// 2. Third-party

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;

// 4. Internal
import com.korur.oba.connect.github.constant.GithubConstant;
import com.korur.oba.connect.github.dto.GithubContributorPayload;
import com.korur.oba.connect.github.dto.GithubPullRequestFilter;
import com.korur.oba.connect.github.dto.GithubPullRequestPagePayload;
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.connect.github.dto.GithubPullRequestSummaryPayload;
import com.korur.oba.connect.github.dto.GithubRepositoryPayload;
import com.korur.oba.connect.github.dto.api.GithubApiIssue;
import com.korur.oba.connect.github.dto.api.GithubApiIssueSearch;
import com.korur.oba.connect.github.dto.api.GithubApiPullRequest;
import com.korur.oba.connect.github.dto.api.GithubApiRepository;
import com.korur.oba.connect.github.dto.api.GithubApiRepositoryList;
import com.korur.oba.connect.github.dto.api.GithubApiUser;
import com.korur.oba.connect.github.exception.ExceptionDomainGithubError;
import com.korur.oba.connect.github.helper.GithubErrorTranslator;
import com.korur.oba.connect.github.helper.GithubSearchQuery;
import com.korur.oba.connect.github.mapper.GithubPayloadMapper;
import com.korur.oba.connect.github.properties.GithubProperties;
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.github.constant.client.GithubClientLogConstant.*;

/**
 * Reads GitHub as the GitHub App installation: its repositories, the pull requests of one
 * repository, a single pull request, and a repository's contributors. It holds no application data and
 * decides no access; the caller checks {@link #isEnabledFor} and resolves a repository ID with
 * {@link #findRepository} before reading that repository, so no request reaches a repository
 * outside the installation.
 *
 * Calls are synchronous, outbound only, with the connect and read timeouts of the client this
 * class is given. A 401 is answered by fetching a new installation token and retrying once; any
 * other failure is logged and thrown as a GHB error (see {@link GithubErrorTranslator}). A
 * pull request or an author GitHub does not know is an empty result, not an error.
 */
public class GithubClient {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(GithubClient.class);

    private static final ParameterizedTypeReference<List<GithubApiPullRequest>> PULL_REQUEST_LIST =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<List<GithubApiUser>> USER_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final GithubAppTokenProvider tokenProvider;
    private final GithubProperties properties;
    private final GithubPayloadMapper payloadMapper;
    private final Clock clock;
    private final LoggingService loggingService;

    private final Object repositoryLock = new Object();
    private volatile CachedRepositories cachedRepositories;

    // Keyed by full name. Names come from the installation's list, which bounds the map.
    private final ConcurrentMap<String, CachedContributors> cachedContributors = new ConcurrentHashMap<>();
    private final Object contributorLock = new Object();

    /**
     * Creates the client.
     *
     * @param restClient     a client with the GitHub API base URL, its headers and timeouts
     * @param tokenProvider  the source of installation tokens
     * @param properties     the App's configuration
     * @param payloadMapper  maps GitHub's payloads
     * @param clock          the clock of the repository and contributor caches
     * @param loggingService the logging service
     */
    public GithubClient(RestClient restClient, GithubAppTokenProvider tokenProvider, GithubProperties properties,
                        GithubPayloadMapper payloadMapper, Clock clock, LoggingService loggingService) {
        this.restClient = restClient;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        this.payloadMapper = payloadMapper;
        this.clock = clock;
        this.loggingService = loggingService;
    }

    /**
     * Tells whether the given organization may use the GitHub integration: every key is set and
     * the organization is the configured one. Every other method of this class may be called only
     * when this is true.
     *
     * @param organizationId the organization of the request
     * @return true when the organization may read GitHub through the installation
     */
    public boolean isEnabledFor(UUID organizationId) {
        return properties.isEnabledFor(organizationId);
    }

    /**
     * Lists the repositories the installation can read, sorted by full name ignoring case. The
     * list is cached for five minutes, so a repository added to the installation shows within
     * that time.
     *
     * @return the repositories, at most 300
     */
    public List<GithubRepositoryPayload> listRepositories() {
        ensureConfigured(OPERATION_LIST_REPOSITORIES);
        CachedRepositories current = cachedRepositories;
        if (current != null && current.isFreshAt(clock.instant())) {
            return current.repositories();
        }
        synchronized (repositoryLock) {
            current = cachedRepositories;
            if (current != null && current.isFreshAt(clock.instant())) {
                return current.repositories();
            }
            List<GithubRepositoryPayload> loaded = loadRepositories();
            cachedRepositories = new CachedRepositories(loaded,
                    clock.instant().plus(GithubConstant.REPOSITORY_CACHE_TTL));
            return loaded;
        }
    }

    /**
     * Finds a repository of the installation by its GitHub ID.
     *
     * @param githubRepositoryId GitHub's numeric repository ID
     * @return the repository, or empty when the installation cannot read it
     */
    public Optional<GithubRepositoryPayload> findRepository(long githubRepositoryId) {
        return listRepositories().stream()
                .filter(repository -> repository.githubRepositoryId() == githubRepositoryId)
                .findFirst();
    }

    /**
     * Lists one page of a repository's pull requests, newest update first.
     *
     * - A search of "#379" or "379" reads that pull request and ignores the filter: one row on
     *   page 1 when it exists.
     * - No text, or text that is empty after cleaning, uses the pull request list when its state
     *   parameter can serve the filter, so the search limits are not spent.
     * - Everything else uses the search API. ":" is removed from the text first so it cannot add a
     *   search qualifier. An author GitHub refuses gives an empty page, and the page says when
     *   GitHub found more than it serves.
     *
     * @param fullName the repository's "owner/name", from {@link #findRepository}
     * @param search   the member's search text, null for none; the caller bounds its length
     * @param filter   the states and authors to keep; the caller validates the authors
     * @param page     the page number, from 1; the caller validates it
     * @return the page, 30 rows at most
     */
    public GithubPullRequestPagePayload listPullRequests(String fullName, String search,
                                                         GithubPullRequestFilter filter, int page) {
        ensureConfigured(OPERATION_LIST_PULL_REQUESTS);
        String term = search == null ? "" : search.trim();
        Matcher numberMatcher = GithubConstant.PULL_REQUEST_NUMBER_SEARCH.matcher(term);
        if (numberMatcher.matches()) {
            return numberPage(fullName, Integer.parseInt(numberMatcher.group(1)), page);
        }
        String text = toSearchText(term, GithubSearchQuery.needsAdvancedSearch(filter));
        Optional<String> listState = text.isEmpty() ? GithubSearchQuery.listState(filter) : Optional.empty();
        return listState.map(state -> listPage(fullName, state, page))
                .orElseGet(() -> searchPage(fullName, text, filter, page));
    }

    /**
     * Reads one pull request with its branches.
     *
     * @param fullName the repository's "owner/name", from {@link #findRepository}
     * @param number   the pull request number
     * @return the pull request, or empty when GitHub has none with this number in the repository
     */
    public Optional<GithubPullRequestPayload> getPullRequest(String fullName, int number) {
        ensureConfigured(OPERATION_GET_PULL_REQUEST);
        RepositoryName repositoryName = RepositoryName.of(fullName);
        return execute(OPERATION_GET_PULL_REQUEST, token -> {
            try {
                GithubApiPullRequest pullRequest = restClient.get()
                        .uri(GithubConstant.PATH_PULL_REQUEST, repositoryName.owner(), repositoryName.repo(), number)
                        .headers(bearer(token))
                        .retrieve()
                        .body(GithubApiPullRequest.class);
                return Optional.ofNullable(pullRequest)
                        .map(found -> payloadMapper.toPullRequestPayload(found, fullName));
            } catch (HttpClientErrorException.NotFound exception) {
                // An absent number is an answer, not a failure: the caller turns it into its own 404.
                loggingService.info(LOG_PREFIX, LogLayer.CLIENT, PULL_REQUEST_ABSENT, number, fullName);
                return Optional.empty();
            }
        });
    }

    /**
     * Lists a repository's contributors, sorted by login ignoring case: the first 100 by commits,
     * with bots. Anonymous contributors are left out, and no email address or name is read. The
     * list is cached per repository for one hour; a failure is thrown and not cached, so the next
     * read tries again.
     *
     * @param fullName the repository's "owner/name", from {@link #findRepository}
     * @return the contributors, empty for an empty repository
     */
    public List<GithubContributorPayload> listContributors(String fullName) {
        ensureConfigured(OPERATION_LIST_CONTRIBUTORS);
        CachedContributors current = cachedContributors.get(fullName);
        if (current != null && current.isFreshAt(clock.instant())) {
            return current.contributors();
        }
        synchronized (contributorLock) {
            current = cachedContributors.get(fullName);
            if (current != null && current.isFreshAt(clock.instant())) {
                return current.contributors();
            }
            List<GithubContributorPayload> loaded = loadContributors(fullName);
            cachedContributors.put(fullName, new CachedContributors(loaded,
                    clock.instant().plus(GithubConstant.CONTRIBUTOR_CACHE_TTL)));
            return loaded;
        }
    }

    private List<GithubContributorPayload> loadContributors(String fullName) {
        RepositoryName repositoryName = RepositoryName.of(fullName);
        List<GithubApiUser> users = execute(OPERATION_LIST_CONTRIBUTORS, token -> restClient.get()
                .uri(builder -> builder.path(GithubConstant.PATH_CONTRIBUTORS)
                        .queryParam(GithubConstant.PARAM_PER_PAGE, GithubConstant.CONTRIBUTORS_PER_PAGE)
                        .build(repositoryName.owner(), repositoryName.repo()))
                .headers(bearer(token))
                .retrieve()
                .body(USER_LIST));
        // GitHub answers an empty repository with 204, which has no body to read.
        List<GithubApiUser> pageItems = users == null ? List.of() : users;
        List<GithubContributorPayload> contributors = pageItems.stream()
                .filter(this::isChoosableAuthor)
                .map(payloadMapper::toContributorPayload)
                .sorted(Comparator.comparing(GithubContributorPayload::login, String.CASE_INSENSITIVE_ORDER))
                .toList();
        loggingService.info(LOG_PREFIX, LogLayer.CLIENT, CONTRIBUTORS_LOADED, contributors.size(), fullName);
        return contributors;
    }

    /**
     * Tells whether a contributor passes the check every author filter goes through, so a listed
     * option can never make the pull request list fail.
     */
    private boolean isChoosableAuthor(GithubApiUser user) {
        return user.login() != null && GithubConstant.LOGIN_PATTERN.matcher(user.login()).matches();
    }

    private List<GithubRepositoryPayload> loadRepositories() {
        List<GithubRepositoryPayload> repositories = new ArrayList<>();
        for (int page = GithubConstant.FIRST_PAGE; page <= GithubConstant.REPOSITORIES_MAX_PAGES; page++) {
            int requestedPage = page;
            GithubApiRepositoryList list = execute(OPERATION_LIST_REPOSITORIES, token -> restClient.get()
                    .uri(builder -> builder.path(GithubConstant.PATH_INSTALLATION_REPOSITORIES)
                            .queryParam(GithubConstant.PARAM_PER_PAGE, GithubConstant.REPOSITORIES_PER_PAGE)
                            .queryParam(GithubConstant.PARAM_PAGE, requestedPage)
                            .build())
                    .headers(bearer(token))
                    .retrieve()
                    .body(GithubApiRepositoryList.class));
            List<GithubApiRepository> pageItems = list == null || list.repositories() == null
                    ? List.of()
                    : list.repositories();
            repositories.addAll(payloadMapper.toRepositoryPayloads(pageItems));
            if (pageItems.size() < GithubConstant.REPOSITORIES_PER_PAGE || repositories.size() >= list.totalCount()) {
                break;
            }
        }
        repositories.sort(Comparator.comparing(GithubRepositoryPayload::fullName, String.CASE_INSENSITIVE_ORDER));
        loggingService.info(LOG_PREFIX, LogLayer.CLIENT, REPOSITORIES_LOADED, repositories.size());
        return List.copyOf(repositories);
    }

    private GithubPullRequestPagePayload numberPage(String fullName, int number, int page) {
        if (page > GithubConstant.FIRST_PAGE) {
            return payloadMapper.toPagePayload(List.of(), page, false, false);
        }
        List<GithubPullRequestSummaryPayload> items = getPullRequest(fullName, number)
                .map(payloadMapper::toSummaryPayload)
                .map(List::of)
                .orElseGet(List::of);
        return payloadMapper.toPagePayload(items, page, false, false);
    }

    private GithubPullRequestPagePayload listPage(String fullName, String state, int page) {
        RepositoryName repositoryName = RepositoryName.of(fullName);
        List<GithubApiPullRequest> pullRequests = execute(OPERATION_LIST_PULL_REQUESTS, token -> restClient.get()
                .uri(builder -> builder.path(GithubConstant.PATH_PULL_REQUESTS)
                        .queryParam(GithubConstant.PARAM_STATE, state)
                        .queryParam(GithubConstant.PARAM_SORT, GithubConstant.SORT_UPDATED)
                        .queryParam(GithubConstant.PARAM_DIRECTION, GithubConstant.ORDER_DESC)
                        .queryParam(GithubConstant.PARAM_PER_PAGE, GithubConstant.PULL_REQUESTS_PER_PAGE)
                        .queryParam(GithubConstant.PARAM_PAGE, page)
                        .build(repositoryName.owner(), repositoryName.repo()))
                .headers(bearer(token))
                .retrieve()
                .body(PULL_REQUEST_LIST));
        List<GithubApiPullRequest> pageItems = pullRequests == null ? List.of() : pullRequests;
        List<GithubPullRequestSummaryPayload> items = pageItems.stream()
                .map(pullRequest -> payloadMapper.toSummaryPayload(pullRequest, fullName))
                .toList();
        // GitHub's pull request list gives no total; a full page means a next one may exist.
        boolean hasMore = pageItems.size() == GithubConstant.PULL_REQUESTS_PER_PAGE;
        return payloadMapper.toPagePayload(items, page, hasMore, false);
    }

    private GithubPullRequestPagePayload searchPage(String fullName, String text, GithubPullRequestFilter filter,
                                                    int page) {
        String query = GithubSearchQuery.build(fullName, text, filter);
        Optional<String> advancedSearch = GithubSearchQuery.needsAdvancedSearch(filter)
                ? Optional.of(GithubConstant.ADVANCED_SEARCH_ENABLED)
                : Optional.empty();
        GithubApiIssueSearch search = execute(OPERATION_SEARCH_PULL_REQUESTS, token -> {
            try {
                return restClient.get()
                        .uri(builder -> builder.path(GithubConstant.PATH_SEARCH_ISSUES)
                                .queryParam(GithubConstant.PARAM_QUERY, GithubConstant.QUERY_TEMPLATE_VARIABLE)
                                .queryParamIfPresent(GithubConstant.PARAM_ADVANCED_SEARCH, advancedSearch)
                                .queryParam(GithubConstant.PARAM_SORT, GithubConstant.SORT_UPDATED)
                                .queryParam(GithubConstant.PARAM_ORDER, GithubConstant.ORDER_DESC)
                                .queryParam(GithubConstant.PARAM_PER_PAGE, GithubConstant.PULL_REQUESTS_PER_PAGE)
                                .queryParam(GithubConstant.PARAM_PAGE, page)
                                .build(query))
                        .headers(bearer(token))
                        .retrieve()
                        .body(GithubApiIssueSearch.class);
            } catch (HttpClientErrorException.UnprocessableEntity exception) {
                if (filter.authors().isEmpty()) {
                    throw exception;
                }
                // A login GitHub does not know is an answer, not a failure: nobody by that name
                // has a pull request here, so the list is empty.
                loggingService.warn(LOG_PREFIX, LogLayer.CLIENT, SEARCH_AUTHOR_UNKNOWN, fullName, filter.authors());
                return null;
            }
        });
        List<GithubApiIssue> pageItems = search == null || search.items() == null ? List.of() : search.items();
        List<GithubPullRequestSummaryPayload> items = pageItems.stream()
                .map(issue -> payloadMapper.toSummaryPayload(issue, fullName))
                .toList();
        int reachable = search == null ? 0 : Math.min(search.totalCount(), GithubConstant.SEARCH_MAX_RESULTS);
        boolean hasMore = pageItems.size() == GithubConstant.PULL_REQUESTS_PER_PAGE
                && (long) page * GithubConstant.PULL_REQUESTS_PER_PAGE < reachable;
        boolean resultsLimited = search != null && search.totalCount() > GithubConstant.SEARCH_MAX_RESULTS;
        return payloadMapper.toPagePayload(items, page, hasMore, resultsLimited);
    }

    /**
     * Removes the qualifier character and collapses whitespace. Advanced search also reads
     * parentheses, quotes and the words AND, OR and NOT as logic, so they go too when the query
     * uses it: the member's text cannot regroup the filters.
     */
    private String toSearchText(String term, boolean advancedSearch) {
        String withoutQualifiers = GithubConstant.SEARCH_QUALIFIER_CHARACTERS.matcher(term)
                .replaceAll(GithubConstant.SEARCH_TERM_SEPARATOR);
        String text = advancedSearch ? withoutOperators(withoutQualifiers) : withoutQualifiers;
        return GithubConstant.WHITESPACE.matcher(text)
                .replaceAll(GithubConstant.SEARCH_TERM_SEPARATOR)
                .trim();
    }

    private String withoutOperators(String text) {
        String withoutGrouping = GithubConstant.ADVANCED_SEARCH_GROUPING_CHARACTERS.matcher(text)
                .replaceAll(GithubConstant.SEARCH_TERM_SEPARATOR);
        return GithubConstant.ADVANCED_SEARCH_OPERATOR_WORDS.matcher(withoutGrouping)
                .replaceAll(GithubConstant.SEARCH_TERM_SEPARATOR);
    }

    /**
     * Runs one authenticated call. A 401 means the cached token was revoked or expired early: the
     * token is dropped and the call is made once more with a new one. A second 401, and every other
     * failure, is logged and thrown as a GHB error.
     */
    private <T> T execute(String operation, Function<String, T> call) {
        String token = tokenProvider.installationToken();
        try {
            try {
                return call.apply(token);
            } catch (HttpClientErrorException.Unauthorized exception) {
                loggingService.warn(LOG_PREFIX, LogLayer.CLIENT, TOKEN_REJECTED_RETRYING, operation);
                tokenProvider.invalidate(token);
                return call.apply(tokenProvider.installationToken());
            }
        } catch (RestClientResponseException exception) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, CALL_FAILED_STATUS, exception,
                    operation, exception.getStatusCode().value());
            throw GithubErrorTranslator.fromResponse(exception);
        } catch (RestClientException exception) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, CALL_FAILED_IO, exception, operation);
            throw GithubErrorTranslator.unreachable();
        }
    }

    private Consumer<HttpHeaders> bearer(String token) {
        return headers -> headers.setBearerAuth(token);
    }

    private void ensureConfigured(String operation) {
        if (!properties.isConfigured()) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, CALL_REFUSED_NOT_CONFIGURED, operation);
            throw new InternalServerErrorException(ExceptionDomainGithubError.INT_GITHUB_NOT_CONFIGURED);
        }
    }

    /**
     * The owner and name of a repository, passed as two path variables because one variable would
     * encode the "/" between them.
     */
    private record RepositoryName(String owner, String repo) {

        private static RepositoryName of(String fullName) {
            String[] parts = fullName == null ? new String[0] : fullName.split(GithubConstant.FULL_NAME_SEPARATOR, 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new InternalServerErrorException(ExceptionInternalError.UNEXPECTED);
            }
            return new RepositoryName(parts[0], parts[1]);
        }
    }

    /**
     * The installation's repositories and when the list stops being fresh.
     */
    private record CachedRepositories(List<GithubRepositoryPayload> repositories, Instant freshUntil) {

        private boolean isFreshAt(Instant now) {
            return now.isBefore(freshUntil);
        }
    }

    /**
     * One repository's contributors and when the list stops being fresh.
     */
    private record CachedContributors(List<GithubContributorPayload> contributors, Instant freshUntil) {

        private boolean isFreshAt(Instant now) {
            return now.isBefore(freshUntil);
        }
    }
}
