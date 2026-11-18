package com.korur.oba.apps.kaizen.pullrequest.controller;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.ServletRequestDataBinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// 2. Third-party
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.pullrequest.constant.controller.PullRequestShareControllerResponseConstant;
import com.korur.oba.apps.kaizen.pullrequest.dto.request.GithubPullRequestQueryRequest;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.GithubContributorResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.GithubPullRequestPageResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.GithubPullRequestResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.GithubPullRequestSummaryResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.GithubRepositoryResponse;
import com.korur.oba.apps.kaizen.pullrequest.mock_data.PullRequestMocks;
import com.korur.oba.apps.kaizen.pullrequest.service.PullRequestShareQueryService;
import com.korur.oba.connect.github.enums.GithubPullRequestState;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class GithubPullRequestControllerUnitTest {

    @Mock
    private PullRequestShareQueryService pullRequestShareQueryService;

    private GithubPullRequestController controller;

    private final UUID organizationId = UUID.randomUUID();
    private final User user = PullRequestMocks.user("Ada", "Lovelace");

    @BeforeEach
    void setUp() {
        controller = new GithubPullRequestController(pullRequestShareQueryService, new LoggingService());
    }

    @Test
    void listGithubRepositories_returnsTheQueryServiceListWith200() {
        // Arrange
        GithubRepositoryResponse repository = new GithubRepositoryResponse(PullRequestMocks.GITHUB_REPOSITORY_ID,
                PullRequestMocks.REPOSITORY_FULL_NAME, true);
        when(pullRequestShareQueryService.listGithubRepositories(user, organizationId))
                .thenReturn(List.of(repository));

        // Act
        ResponseEntity<CustomResponse<List<GithubRepositoryResponse>>> response =
                controller.listGithubRepositories(user, organizationId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message())
                .isEqualTo(PullRequestShareControllerResponseConstant.RESPONSE_GITHUB_REPOSITORY_LIST_RETRIEVED);
        assertThat(response.getBody().data()).containsExactly(repository);
    }

    @Test
    void listGithubRepositories_githubNotConnected_propagatesNotFound() {
        // Arrange
        when(pullRequestShareQueryService.listGithubRepositories(user, organizationId)).thenThrow(
                new NotFoundException(ExceptionDomainKaizenError.NOT_GITHUB_NOT_CONFIGURED));

        // Act and assert
        assertThatThrownBy(() -> controller.listGithubRepositories(user, organizationId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listGithubPullRequests_returnsThePageWith200() {
        // Arrange
        GithubPullRequestQueryRequest query = new GithubPullRequestQueryRequest();
        query.setSearch("menu");
        query.setPage(2);
        GithubPullRequestPageResponse page = new GithubPullRequestPageResponse(List.of(
                new GithubPullRequestSummaryResponse(379, "Mobile menu", GithubPullRequestState.OPEN, "ada-dev",
                        Instant.parse("2026-10-27T08:00:00Z"), "https://github.com/senseylabs/fabrikam/pull/379")),
                2, true, false);
        when(pullRequestShareQueryService.listGithubPullRequests(user, organizationId,
                PullRequestMocks.GITHUB_REPOSITORY_ID, query)).thenReturn(page);

        // Act
        ResponseEntity<CustomResponse<GithubPullRequestPageResponse>> response =
                controller.listGithubPullRequests(user, organizationId, PullRequestMocks.GITHUB_REPOSITORY_ID, query);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(PullRequestShareControllerResponseConstant.RESPONSE_GITHUB_PULL_REQUEST_LIST_RETRIEVED);
        assertThat(response.getBody().data()).isEqualTo(page);
    }

    @Test
    void getGithubPullRequest_returnsThePreviewWith200() {
        // Arrange
        GithubPullRequestResponse preview = new GithubPullRequestResponse(379, "Mobile menu",
                GithubPullRequestState.OPEN, "ada-dev", Instant.parse("2026-10-27T08:00:00Z"),
                "https://github.com/senseylabs/fabrikam/pull/379", "feature/ACME-241-mobile-menu", "main", "ACME-241",
                null);
        when(pullRequestShareQueryService.getGithubPullRequest(user, organizationId,
                PullRequestMocks.GITHUB_REPOSITORY_ID, 379)).thenReturn(preview);

        // Act
        ResponseEntity<CustomResponse<GithubPullRequestResponse>> response =
                controller.getGithubPullRequest(user, organizationId, PullRequestMocks.GITHUB_REPOSITORY_ID, 379);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(PullRequestShareControllerResponseConstant.RESPONSE_GITHUB_PULL_REQUEST_RETRIEVED);
        assertThat(response.getBody().data()).isEqualTo(preview);
    }

    @Test
    void listGithubContributors_returnsTheLoginsWith200() {
        // Arrange
        List<GithubContributorResponse> contributors = List.of(new GithubContributorResponse("ada-dev"),
                new GithubContributorResponse("github-actions[bot]"));
        when(pullRequestShareQueryService.listGithubContributors(user, organizationId,
                PullRequestMocks.GITHUB_REPOSITORY_ID)).thenReturn(contributors);

        // Act
        ResponseEntity<CustomResponse<List<GithubContributorResponse>>> response =
                controller.listGithubContributors(user, organizationId, PullRequestMocks.GITHUB_REPOSITORY_ID);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message())
                .isEqualTo(PullRequestShareControllerResponseConstant.RESPONSE_GITHUB_CONTRIBUTOR_LIST_RETRIEVED);
        assertThat(response.getBody().data()).containsExactlyElementsOf(contributors);
    }

    @Test
    void listGithubContributors_serviceRefuses_propagatesTheError() {
        // Arrange
        when(pullRequestShareQueryService.listGithubContributors(user, organizationId, 999L)).thenThrow(
                new NotFoundException(ExceptionDomainKaizenError.NOT_GITHUB_REPOSITORY_FOUND));

        // Act and assert
        assertThatThrownBy(() -> controller.listGithubContributors(user, organizationId, 999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listGithubPullRequests_resultsLimitedPage_isReturnedAsIs() {
        // Arrange
        GithubPullRequestQueryRequest query = new GithubPullRequestQueryRequest();
        GithubPullRequestPageResponse page = new GithubPullRequestPageResponse(List.of(), 1, false, true);
        when(pullRequestShareQueryService.listGithubPullRequests(user, organizationId,
                PullRequestMocks.GITHUB_REPOSITORY_ID, query)).thenReturn(page);

        // Act
        ResponseEntity<CustomResponse<GithubPullRequestPageResponse>> response =
                controller.listGithubPullRequests(user, organizationId, PullRequestMocks.GITHUB_REPOSITORY_ID, query);

        // Assert
        assertThat(response.getBody().data().resultsLimited()).isTrue();
    }

    @Nested
    class QueryBindingAndValidation {

        private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        private GithubPullRequestQueryRequest bind(MockHttpServletRequest request) {
            GithubPullRequestQueryRequest query = new GithubPullRequestQueryRequest();
            ServletRequestDataBinder binder = new ServletRequestDataBinder(query);
            binder.setConversionService(new DefaultFormattingConversionService());
            binder.bind(request);
            assertThat(binder.getBindingResult().hasErrors()).isFalse();
            return query;
        }

        private MockHttpServletRequest params(String name, String... values) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter(name, values);
            return request;
        }

        @Test
        void commaSeparatedStatesAndAuthors_bindAsLists() {
            // Arrange
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter("states", "OPEN,DRAFT");
            request.setParameter("authors", "octo,dependabot[bot]");

            // Act
            GithubPullRequestQueryRequest query = bind(request);

            // Assert
            assertThat(query.getStates()).containsExactly(GithubPullRequestState.OPEN, GithubPullRequestState.DRAFT);
            assertThat(query.getAuthors()).containsExactly("octo", "dependabot[bot]");
            assertThat(validator.validate(query)).isEmpty();
        }

        @Test
        void repeatedParameters_bindAsLists() {
            // Act
            GithubPullRequestQueryRequest query = bind(params("states", "MERGED", "CLOSED"));

            // Assert
            assertThat(query.getStates()).containsExactly(GithubPullRequestState.MERGED, GithubPullRequestState.CLOSED);
        }

        @Test
        void unknownState_failsBinding() {
            // Arrange
            GithubPullRequestQueryRequest query = new GithubPullRequestQueryRequest();
            ServletRequestDataBinder binder = new ServletRequestDataBinder(query);
            binder.setConversionService(new DefaultFormattingConversionService());

            // Act
            binder.bind(params("states", "BOGUS"));

            // Assert
            assertThat(binder.getBindingResult().hasFieldErrors("states")).isTrue();
        }

        @Test
        void fiveAuthors_pass() {
            // Arrange
            GithubPullRequestQueryRequest query = bind(params("authors", "a,b,c,d,e"));

            // Act and assert
            assertThat(validator.validate(query)).isEmpty();
        }

        @Test
        void sixthAuthor_isRefused() {
            // Arrange
            GithubPullRequestQueryRequest query = bind(params("authors", "a,b,c,d,e,f"));

            // Act
            Set<ConstraintViolation<GithubPullRequestQueryRequest>> violations = validator.validate(query);

            // Assert
            assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("authors");
        }

        @ParameterizedTest
        @ValueSource(strings = {"a b", "a:b", "-a", "a OR b", "a)"})
        void authorThatIsNotALogin_isRefused(String login) {
            // Arrange
            GithubPullRequestQueryRequest query = new GithubPullRequestQueryRequest();
            query.setAuthors(List.of("octo", login));

            // Act
            Set<ConstraintViolation<GithubPullRequestQueryRequest>> violations = validator.validate(query);

            // Assert
            assertThat(violations).hasSize(1);
        }
    }
}
