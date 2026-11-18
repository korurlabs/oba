package com.korur.oba.apps.kazan.pullrequest.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.response.ChatLinkedTicketResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.pullrequest.dto.request.GithubPullRequestQueryRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.request.PullRequestShareCreateRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.request.PullRequestShareQueryRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubContributorResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubPullRequestPageResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubPullRequestResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubPullRequestSummaryResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubRepositoryResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestSharePreviewResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kazan.pullrequest.enums.PullRequestShareStatus;
import com.korur.oba.apps.kazan.pullrequest.helper.PullRequestShareSummaryRenderer;
import com.korur.oba.apps.kazan.pullrequest.mapper.PullRequestShareMapperHelper;
import com.korur.oba.apps.kazan.pullrequest.mapper.PullRequestShareMapperImpl;
import com.korur.oba.apps.kazan.pullrequest.mock_data.PullRequestMocks;
import com.korur.oba.apps.kazan.pullrequest.repository.PullRequestShareRepository;
import com.korur.oba.apps.kazan.pullrequest.repository.PullRequestShareReviewerRepository;
import com.korur.oba.apps.kazan.pullrequest.validator.PullRequestShareValidator;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.service.TicketQueryService;
import com.korur.oba.connect.github.client.GithubClient;
import com.korur.oba.connect.github.dto.GithubContributorPayload;
import com.korur.oba.connect.github.dto.GithubPullRequestFilter;
import com.korur.oba.connect.github.dto.GithubPullRequestPagePayload;
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.connect.github.dto.GithubPullRequestSummaryPayload;
import com.korur.oba.connect.github.dto.GithubRepositoryPayload;
import com.korur.oba.connect.github.enums.GithubPullRequestState;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Real validator, mapper and helper around mocked repositories and a mocked GitHub client, so the
 * picker and share reads run as in production and GitHub is never called.
 */
@ExtendWith(MockitoExtension.class)
class PullRequestShareQueryServiceImplUnitTest {

    @Mock
    private PullRequestShareRepository pullRequestShareRepository;
    @Mock
    private AppAccessValidator appAccessValidator;
    @Mock
    private PullRequestShareReviewerRepository pullRequestShareReviewerRepository;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;
    @Mock
    private GithubClient githubClient;
    @Mock
    private TicketQueryService ticketQueryService;

    private PullRequestShareQueryServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final User reader = PullRequestMocks.user("Alan", "Turing");
    private final User author = PullRequestMocks.user("Ada", "Lovelace");

    @BeforeEach
    void setUp() {
        PullRequestShareMapperImpl mapper = new PullRequestShareMapperImpl();
        PullRequestShareValidator validator = new PullRequestShareValidator(pullRequestShareRepository,
                pullRequestShareReviewerRepository, organizationMemberQueryService, appAccessValidator, chatChannelQueryService,
                githubClient, new LoggingService());
        PullRequestShareMapperHelper helper = new PullRequestShareMapperHelper(mapper, ticketQueryService,
                new PullRequestShareSummaryRenderer(), chatChannelQueryService, validator);
        service = new PullRequestShareQueryServiceImpl(pullRequestShareRepository, validator, mapper, helper,
                githubClient);

        lenient().when(ticketQueryService.getAccessibleTicketsByKeys(any(), eq(organizationId), any())).thenReturn(List.of());
    }

    private void githubEnabled() {
        when(githubClient.isEnabledFor(organizationId)).thenReturn(true);
    }

    private void githubNotConnected() {
        when(githubClient.isEnabledFor(organizationId)).thenReturn(false);
    }

    @Nested
    class ListGithubRepositories {

        @Test
        void githubEnabled_returnsTheInstallationRepositories() {
            // Arrange
            githubEnabled();
            GithubRepositoryPayload repository = PullRequestMocks.repository();
            when(githubClient.listRepositories()).thenReturn(List.of(repository));

            // Act
            List<GithubRepositoryResponse> result = service.listGithubRepositories(reader, organizationId);

            // Assert
            assertThat(result).containsExactly(new GithubRepositoryResponse(repository.githubRepositoryId(),
                    repository.fullName(), repository.privateRepository()));
        }

        @Test
        void githubNotConnected_throwsNotFound() {
            // Arrange
            githubNotConnected();

            // Act and assert
            assertThatThrownBy(() -> service.listGithubRepositories(reader, organizationId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(ExceptionDomainKazanError.NOT_GITHUB_NOT_CONFIGURED.getCode()));
        }
    }

    @Nested
    class ListGithubContributors {

        @Test
        void validRepository_returnsTheMappedLoginsOfThatRepository() {
            // Arrange
            githubEnabled();
            GithubRepositoryPayload repository = PullRequestMocks.repository();
            when(githubClient.findRepository(repository.githubRepositoryId())).thenReturn(Optional.of(repository));
            when(githubClient.listContributors(repository.fullName()))
                    .thenReturn(List.of(new GithubContributorPayload("ada"),
                            new GithubContributorPayload("github-actions[bot]")));

            // Act
            List<GithubContributorResponse> result = service.listGithubContributors(reader, organizationId,
                    repository.githubRepositoryId());

            // Assert
            assertThat(result).containsExactly(new GithubContributorResponse("ada"),
                    new GithubContributorResponse("github-actions[bot]"));
        }

        @Test
        void checksGithubThenRepositoryBeforeTheClientRead() {
            // Arrange
            githubEnabled();
            GithubRepositoryPayload repository = PullRequestMocks.repository();
            when(githubClient.findRepository(repository.githubRepositoryId())).thenReturn(Optional.of(repository));
            when(githubClient.listContributors(repository.fullName())).thenReturn(List.of());

            // Act
            service.listGithubContributors(reader, organizationId, repository.githubRepositoryId());

            // Assert
            InOrder order = inOrder(githubClient);
            order.verify(githubClient).isEnabledFor(organizationId);
            order.verify(githubClient).findRepository(repository.githubRepositoryId());
            order.verify(githubClient).listContributors(repository.fullName());
        }

        @Test
        void githubNotConnected_throwsNotFoundBeforeAnyRead() {
            // Arrange
            githubNotConnected();

            // Act and assert
            assertThatThrownBy(() -> service.listGithubContributors(reader, organizationId,
                    PullRequestMocks.GITHUB_REPOSITORY_ID))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(ExceptionDomainKazanError.NOT_GITHUB_NOT_CONFIGURED.getCode()));
            verify(githubClient, never()).listContributors(anyString());
        }

        @Test
        void repositoryOutsideTheInstallation_throwsNotFoundAndNeverReachesTheContributorRead() {
            // Arrange
            githubEnabled();
            when(githubClient.findRepository(999L)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.listGithubContributors(reader, organizationId, 999L))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(ExceptionDomainKazanError.NOT_GITHUB_REPOSITORY_FOUND.getCode()));
            verify(githubClient, never()).listContributors(anyString());
        }
    }

    @Nested
    class ListGithubPullRequests {

        @Test
        void validQuery_returnsTheMappedPage() {
            // Arrange
            githubEnabled();
            GithubRepositoryPayload repository = PullRequestMocks.repository();
            Instant updatedAt = Instant.parse("2026-10-27T08:00:00Z");
            GithubPullRequestSummaryPayload row = new GithubPullRequestSummaryPayload(379, "Mobile menu",
                    GithubPullRequestState.DRAFT, "ada-dev", updatedAt, "https://github.com/x/pull/379");
            when(githubClient.findRepository(repository.githubRepositoryId())).thenReturn(Optional.of(repository));
            when(githubClient.listPullRequests(repository.fullName(), "menu", GithubPullRequestFilter.none(), 2))
                    .thenReturn(new GithubPullRequestPagePayload(List.of(row), 2, true, false));

            // Act
            GithubPullRequestPageResponse result = service.listGithubPullRequests(reader, organizationId,
                    repository.githubRepositoryId(), query("menu", 2));

            // Assert
            assertThat(result.page()).isEqualTo(2);
            assertThat(result.hasMore()).isTrue();
            assertThat(result.resultsLimited()).isFalse();
            assertThat(result.items()).singleElement().satisfies((GithubPullRequestSummaryResponse item) -> {
                assertThat(item.number()).isEqualTo(379);
                assertThat(item.state()).isEqualTo(GithubPullRequestState.DRAFT);
                assertThat(item.updatedAt()).isEqualTo(updatedAt);
            });
        }

        @Test
        void statesAndAuthors_reachTheClientAsOneFilterAndResultsLimitedIsMapped() {
            // Arrange
            githubEnabled();
            GithubRepositoryPayload repository = PullRequestMocks.repository();
            GithubPullRequestQueryRequest request = query("menu", 1);
            request.setStates(List.of(GithubPullRequestState.OPEN, GithubPullRequestState.MERGED));
            request.setAuthors(List.of("octo", "dependabot[bot]"));
            GithubPullRequestFilter expected = new GithubPullRequestFilter(
                    List.of(GithubPullRequestState.OPEN, GithubPullRequestState.MERGED),
                    List.of("octo", "dependabot[bot]"));
            when(githubClient.findRepository(repository.githubRepositoryId())).thenReturn(Optional.of(repository));
            when(githubClient.listPullRequests(repository.fullName(), "menu", expected, 1))
                    .thenReturn(new GithubPullRequestPagePayload(List.of(), 1, false, true));

            // Act
            GithubPullRequestPageResponse result = service.listGithubPullRequests(reader, organizationId,
                    repository.githubRepositoryId(), request);

            // Assert
            assertThat(result.resultsLimited()).isTrue();
            verify(githubClient).listPullRequests(repository.fullName(), "menu", expected, 1);
        }

        @Test
        void sixAuthors_throwValidationBeforeLookingUpTheRepository() {
            // Arrange
            githubEnabled();
            GithubPullRequestQueryRequest request = query(null, 1);
            request.setAuthors(List.of("a", "b", "c", "d", "e", "f"));

            // Act and assert
            assertThatThrownBy(() -> service.listGithubPullRequests(reader, organizationId,
                    PullRequestMocks.GITHUB_REPOSITORY_ID, request)).isInstanceOf(ValidationException.class);
            verify(githubClient, never()).findRepository(anyLong());
            verify(githubClient, never()).listPullRequests(anyString(), any(), any(), anyInt());
        }

        @Test
        void authorThatIsNotALogin_throwsValidationAndNeverAsksGithub() {
            // Arrange
            githubEnabled();
            GithubPullRequestQueryRequest request = query(null, 1);
            request.setAuthors(List.of("octo", "a:b"));

            // Act and assert
            assertThatThrownBy(() -> service.listGithubPullRequests(reader, organizationId,
                    PullRequestMocks.GITHUB_REPOSITORY_ID, request)).isInstanceOf(ValidationException.class);
            verify(githubClient, never()).listPullRequests(anyString(), any(), any(), anyInt());
        }

        @Test
        void pageBelowOne_throwsValidationBeforeLookingUpTheRepository() {
            // Arrange
            githubEnabled();

            // Act and assert
            assertThatThrownBy(() -> service.listGithubPullRequests(reader, organizationId,
                    PullRequestMocks.GITHUB_REPOSITORY_ID, query(null, 0)))
                    .isInstanceOf(ValidationException.class);
            verify(githubClient, never()).findRepository(anyLong());
        }

        @Test
        void searchOverTheLimit_throwsValidation() {
            // Arrange
            githubEnabled();

            // Act and assert
            assertThatThrownBy(() -> service.listGithubPullRequests(reader, organizationId,
                    PullRequestMocks.GITHUB_REPOSITORY_ID, query("a".repeat(101), 1)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void repositoryNotInTheInstallation_throwsNotFound() {
            // Arrange
            githubEnabled();
            when(githubClient.findRepository(anyLong())).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.listGithubPullRequests(reader, organizationId, 999L, query(null, 1)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(ExceptionDomainKazanError.NOT_GITHUB_REPOSITORY_FOUND.getCode()));
        }

        @Test
        void githubNotConnected_throwsNotFound() {
            // Arrange
            githubNotConnected();

            // Act and assert
            assertThatThrownBy(() -> service.listGithubPullRequests(reader, organizationId,
                    PullRequestMocks.GITHUB_REPOSITORY_ID, query(null, 1)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(ExceptionDomainKazanError.NOT_GITHUB_NOT_CONFIGURED.getCode()));
        }

        private GithubPullRequestQueryRequest query(String search, int page) {
            GithubPullRequestQueryRequest request = new GithubPullRequestQueryRequest();
            request.setSearch(search);
            request.setPage(page);
            return request;
        }
    }

    @Nested
    class GetGithubPullRequest {

        @Test
        void pullRequestWithOpenableTicket_returnsPreviewWithLinkedTicket() {
            // Arrange
            githubEnabled();
            GithubRepositoryPayload repository = PullRequestMocks.repository();
            GithubPullRequestPayload pullRequest = PullRequestMocks.pullRequest(379, "Mobile menu",
                    "feature/ACME-241-mobile-menu");
            Ticket ticket = ChatMocks.ticket("ACME-241", "Mobile menu");
            when(githubClient.findRepository(repository.githubRepositoryId())).thenReturn(Optional.of(repository));
            when(githubClient.getPullRequest(repository.fullName(), 379)).thenReturn(Optional.of(pullRequest));
            when(ticketQueryService.getAccessibleTicketsByKeys(reader.getId(), organizationId, Set.of("ACME-241")))
                    .thenReturn(List.of(ticket));

            // Act
            GithubPullRequestResponse result = service.getGithubPullRequest(reader, organizationId,
                    repository.githubRepositoryId(), 379);

            // Assert
            assertThat(result.number()).isEqualTo(379);
            assertThat(result.headRef()).isEqualTo("feature/ACME-241-mobile-menu");
            assertThat(result.ticketKey()).isEqualTo("ACME-241");
            assertThat(result.linkedTicket()).isEqualTo(
                    new ChatLinkedTicketResponse("ACME-241", ticket.getId(), ticket.getBoard().getId()));
        }

        @Test
        void pullRequestWithoutTicketKey_returnsPreviewWithoutTicketLookup() {
            // Arrange
            githubEnabled();
            GithubRepositoryPayload repository = PullRequestMocks.repository();
            GithubPullRequestPayload pullRequest = PullRequestMocks.pullRequest(12, "Tidy", "chore/tidy");
            when(githubClient.findRepository(repository.githubRepositoryId())).thenReturn(Optional.of(repository));
            when(githubClient.getPullRequest(repository.fullName(), 12)).thenReturn(Optional.of(pullRequest));

            // Act
            GithubPullRequestResponse result = service.getGithubPullRequest(reader, organizationId,
                    repository.githubRepositoryId(), 12);

            // Assert
            assertThat(result.ticketKey()).isNull();
            assertThat(result.linkedTicket()).isNull();
            verifyNoInteractions(ticketQueryService);
        }

        @Test
        void unknownPullRequest_throwsNotFound() {
            // Arrange
            githubEnabled();
            GithubRepositoryPayload repository = PullRequestMocks.repository();
            when(githubClient.findRepository(repository.githubRepositoryId())).thenReturn(Optional.of(repository));
            when(githubClient.getPullRequest(anyString(), anyInt())).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.getGithubPullRequest(reader, organizationId,
                    repository.githubRepositoryId(), 5))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(ExceptionDomainKazanError.NOT_GITHUB_PULL_REQUEST_FOUND.getCode()));
        }

        @Test
        void unknownRepository_throwsNotFound() {
            // Arrange
            githubEnabled();
            when(githubClient.findRepository(anyLong())).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.getGithubPullRequest(reader, organizationId, 999L, 5))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(ExceptionDomainKazanError.NOT_GITHUB_REPOSITORY_FOUND.getCode()));
        }
    }

    @Nested
    class ListShares {

        @Test
        void request_pagesByCreatedAtDescendingAndIgnoresTheRequestSort() {
            // Arrange
            PullRequestShareQueryRequest request = new PullRequestShareQueryRequest();
            request.setPage(2);
            request.setAmount(10);
            request.setSortBy("note");
            request.setSortDirection("asc");
            when(pullRequestShareRepository.findAll(ArgumentMatchers.<Specification<PullRequestShare>>any(),
                    any(Pageable.class))).thenReturn(Page.empty());

            // Act
            service.listShares(reader, organizationId, request);

            // Assert
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(pullRequestShareRepository).findAll(ArgumentMatchers.<Specification<PullRequestShare>>any(),
                    pageable.capture());
            assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
            assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
            assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        @Test
        void pageOfShares_returnsResponsesWithChatLocationAndPagingMetadata() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            ChatMessage entry = ChatMocks.pullRequestShareMessage(author, organizationId, share.getId());
            when(chatChannelQueryService.findPullRequestShareMessages(List.of(share.getId())))
                    .thenReturn(Map.of(share.getId(), entry));
            when(pullRequestShareRepository.findAll(ArgumentMatchers.<Specification<PullRequestShare>>any(),
                    any(Pageable.class))).thenReturn(new PageImpl<>(List.of(share), PageRequest.of(0, 25), 1));

            // Act
            Page<PullRequestShareResponse> result = service.listShares(reader, organizationId,
                    new PullRequestShareQueryRequest());

            // Assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).singleElement().satisfies((PullRequestShareResponse response) -> {
                assertThat(response.id()).isEqualTo(share.getId());
                assertThat(response.authorFirstName()).isEqualTo("Ada");
                assertThat(response.channelId()).isEqualTo(entry.getChannelId());
                assertThat(response.messageId()).isEqualTo(entry.getId());
            });
        }
    }

    @Nested
    class PreviewShare {

        private void githubReady() {
            GithubRepositoryPayload repository = PullRequestMocks.repository();
            when(githubClient.isEnabledFor(organizationId)).thenReturn(true);
            when(githubClient.findRepository(repository.githubRepositoryId())).thenReturn(Optional.of(repository));
            when(githubClient.getPullRequest(repository.fullName(), 379)).thenReturn(Optional.of(
                    PullRequestMocks.pullRequest(379, "Mobile menu", "feature/ACME-241-mobile-menu")));
        }

        @Test
        void validRequest_returnsPullRequestAndTheTextOfTheCardAndSavesNothing() {
            // Arrange
            githubReady();
            when(chatChannelQueryService.findPullRequestChannelId(organizationId))
                    .thenReturn(Optional.of(UUID.randomUUID()));
            PullRequestShareCreateRequest request = PullRequestMocks.createRequest(PullRequestShareStatus.IN_REVIEW,
                    "please look", List.of());
            PullRequestShare saved = PullRequestMocks.share(author, organizationId);
            saved.setNote("please look");
            when(pullRequestShareRepository.findByIdAndOrganizationId(saved.getId(), organizationId))
                    .thenReturn(Optional.of(saved));

            // Act
            PullRequestSharePreviewResponse preview = service.previewShare(reader, organizationId, request);

            // Assert
            assertThat(preview.pullRequest().number()).isEqualTo(379);
            assertThat(preview.pullRequest().headRef()).isEqualTo("feature/ACME-241-mobile-menu");
            assertThat(preview.summaryText()).isEqualTo(new PullRequestShareSummaryRenderer().render("ACME-241",
                    "Mobile menu", "https://github.com/senseylabs/fabrikam/pull/379", PullRequestShareStatus.IN_REVIEW,
                    "please look"));
            assertThat(preview.summaryText())
                    .as("the preview text equals the text of a saved card with the same fields")
                    .isEqualTo(service.getShare(reader, organizationId, saved.getId()).summaryText());
            verify(pullRequestShareRepository, never()).save(any(PullRequestShare.class));
            verify(pullRequestShareRepository, never()).saveAndFlush(any(PullRequestShare.class));
        }

        @Test
        void validRequest_runsTheCreateChecksInTheCreateOrder() {
            // Arrange
            githubReady();
            when(chatChannelQueryService.findPullRequestChannelId(organizationId))
                    .thenReturn(Optional.of(UUID.randomUUID()));

            // Act
            service.previewShare(reader, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null, List.of()));

            // Assert
            InOrder order = inOrder(githubClient, chatChannelQueryService);
            order.verify(githubClient).isEnabledFor(organizationId);
            order.verify(githubClient).getPullRequest(any(), eq(379));
            order.verify(chatChannelQueryService).findPullRequestChannelId(organizationId);
        }

        @Test
        void inaccessibleRelatedTicket_throwsNotFoundBeforeGithubAndSavesNothing() {
            // Arrange
            UUID foreign = UUID.randomUUID();
            when(ticketQueryService.getAccessibleTickets(eq(reader.getId()), eq(organizationId), any())).thenReturn(List.of());

            // Act and Assert
            assertThatThrownBy(() -> service.previewShare(reader, organizationId,
                    PullRequestMocks.createRequestWithTickets(List.of(foreign))))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_TICKET_FOUND.getCode());
            verify(githubClient, never()).getPullRequest(any(), anyInt());
            verify(pullRequestShareRepository, never()).saveAndFlush(any(PullRequestShare.class));
        }

        @Test
        void repeatedOrElevenRelatedTickets_throwValidationBeforeGithub() {
            // Arrange
            UUID repeated = UUID.randomUUID();
            List<UUID> eleven = Stream.generate(UUID::randomUUID).limit(11).toList();

            // Act and Assert
            assertThatThrownBy(() -> service.previewShare(reader, organizationId,
                    PullRequestMocks.createRequestWithTickets(List.of(repeated, repeated))))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_PULL_REQUEST_SHARE_INVALID.getCode());
            assertThatThrownBy(() -> service.previewShare(reader, organizationId,
                    PullRequestMocks.createRequestWithTickets(eleven)))
                    .isInstanceOf(ValidationException.class);
            verify(githubClient, never()).getPullRequest(any(), anyInt());
        }

        @Test
        void reviewerIsTheCaller_throwsValidationBeforeAskingGithub() {
            // Act and assert
            assertThatThrownBy(() -> service.previewShare(reader, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null, List.of(reader.getId()))))
                    .isInstanceOf(ValidationException.class);
            verifyNoInteractions(githubClient);
        }

        @Test
        void noteOf501Characters_throwsValidation() {
            // Act and assert
            assertThatThrownBy(() -> service.previewShare(reader, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, "n".repeat(501), List.of())))
                    .isInstanceOf(ValidationException.class);
            verifyNoInteractions(githubClient);
        }

        @Test
        void githubNotConnected_throwsNotFound() {
            // Arrange
            githubNotConnected();

            // Act and assert
            assertThatThrownBy(() -> service.previewShare(reader, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null, List.of())))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(ExceptionDomainKazanError.NOT_GITHUB_NOT_CONFIGURED.getCode()));
        }

        @Test
        void pullRequestChannelAbsent_throwsValidationAsTheCreateDoes() {
            // Arrange
            githubReady();
            when(chatChannelQueryService.findPullRequestChannelId(organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.previewShare(reader, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null, List.of())))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(ExceptionDomainKazanError.VAL_PULL_REQUEST_CHANNEL_ABSENT.getCode()));
            verifyNoInteractions(pullRequestShareRepository);
        }
    }

    @Nested
    class GetShare {

        @Test
        void shareExists_returnsTheShareForTheReader() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            when(pullRequestShareRepository.findByIdAndOrganizationId(share.getId(), organizationId))
                    .thenReturn(Optional.of(share));

            // Act
            PullRequestShareResponse result = service.getShare(reader, organizationId, share.getId());

            // Assert
            assertThat(result.id()).isEqualTo(share.getId());
            assertThat(result.authorId()).isEqualTo(author.getId());
            assertThat(result.summaryText()).contains("https://github.com/senseylabs/fabrikam/pull/379");
        }

        @Test
        void shareOfAnotherOrganization_throwsNotFound() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            when(pullRequestShareRepository.findByIdAndOrganizationId(shareId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.getShare(reader, organizationId, shareId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(ExceptionDomainKazanError.NOT_PULL_REQUEST_SHARE_FOUND.getCode()));
        }
    }

    @Nested
    class GetSharesByIds {

        @Test
        void nullOrEmptyIds_returnEmptyWithoutAQuery() {
            // Act
            Map<UUID, PullRequestShareResponse> forNull = service.getSharesByIds(reader, organizationId, null);
            Map<UUID, PullRequestShareResponse> forEmpty = service.getSharesByIds(reader, organizationId, Set.of());

            // Assert
            assertThat(forNull).isEmpty();
            assertThat(forEmpty).isEmpty();
            verifyNoInteractions(pullRequestShareRepository);
        }

        @Test
        void foundAndMissingIds_returnFoundSharesInRequestOrder() {
            // Arrange
            PullRequestShare first = PullRequestMocks.share(author, organizationId);
            PullRequestShare second = PullRequestMocks.share(author, organizationId);
            UUID missingId = UUID.randomUUID();
            List<UUID> requested = List.of(second.getId(), missingId, first.getId(), second.getId());
            when(pullRequestShareRepository.findAllByIdInAndOrganizationId(
                    Set.of(second.getId(), missingId, first.getId()), organizationId))
                    .thenReturn(List.of(first, second));

            // Act
            Map<UUID, PullRequestShareResponse> result = service.getSharesByIds(reader, organizationId, requested);

            // Assert
            assertThat(result.keySet()).containsExactly(second.getId(), first.getId());
            assertThat(result.get(first.getId()).id()).isEqualTo(first.getId());
        }

        @Test
        void nullReader_linksNoTicket() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            Collection<UUID> ids = List.of(share.getId());
            when(pullRequestShareRepository.findAllByIdInAndOrganizationId(Set.of(share.getId()), organizationId))
                    .thenReturn(List.of(share));

            // Act
            Map<UUID, PullRequestShareResponse> result = service.getSharesByIds(null, organizationId, ids);

            // Assert
            assertThat(result.get(share.getId()).linkedTicket()).isNull();
            verifyNoInteractions(ticketQueryService);
        }
    }

    @Nested
    class FindShare {

        @Test
        void existingShare_returnsIt() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            when(pullRequestShareRepository.findByIdAndOrganizationId(share.getId(), organizationId))
                    .thenReturn(Optional.of(share));

            // Act
            Optional<PullRequestShare> result = service.findShare(organizationId, share.getId());

            // Assert
            assertThat(result).containsSame(share);
        }

        @Test
        void missingShare_returnsEmpty() {
            // Act
            Optional<PullRequestShare> result = service.findShare(organizationId, UUID.randomUUID());

            // Assert
            assertThat(result).isEmpty();
        }
    }
}
