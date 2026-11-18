package com.korur.oba.apps.kazan.pullrequest.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageCreateRequest;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.chat.service.ChatMessageService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareTicketResponse;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShareReviewer;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShareTicket;
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
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.connect.github.dto.GithubRepositoryPayload;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Real validator, mapper, helper and renderer around mocked repositories, GitHub client and chat, so
 * the rules of a share are exercised as they run and GitHub is never called.
 */
@ExtendWith(MockitoExtension.class)
class PullRequestShareServiceImplUnitTest {

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
    private UserQueryService userQueryService;
    @Mock
    private ChatMessageService chatMessageService;
    @Mock
    private TicketQueryService ticketQueryService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private PullRequestShareServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final User author = PullRequestMocks.user("Ada", "Lovelace");
    private final User reviewerOne = PullRequestMocks.user("Alan", "Turing");
    private final User reviewerTwo = PullRequestMocks.user("Grace", "Hopper");

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        PullRequestShareMapperImpl mapper = new PullRequestShareMapperImpl();
        PullRequestShareValidator validator = new PullRequestShareValidator(pullRequestShareRepository,
                pullRequestShareReviewerRepository, organizationMemberQueryService, appAccessValidator, chatChannelQueryService,
                githubClient, loggingService);
        PullRequestShareMapperHelper helper = new PullRequestShareMapperHelper(mapper, ticketQueryService,
                new PullRequestShareSummaryRenderer(), chatChannelQueryService, validator);
        service = new PullRequestShareServiceImpl(pullRequestShareRepository, validator, mapper, helper,
                userQueryService, chatMessageService,
                new TransactionTemplate(transactionManager), loggingService);

        lenient().when(pullRequestShareRepository.saveAndFlush(any(PullRequestShare.class)))
                .thenAnswer(invocation -> {
                    PullRequestShare saved = invocation.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId(UUID.randomUUID());
                    }
                    return saved;
                });
        lenient().when(ticketQueryService.getAccessibleTicketsByKeys(any(), eq(organizationId), any())).thenReturn(List.of());
    }

    private void githubReady() {
        GithubRepositoryPayload repository = PullRequestMocks.repository();
        GithubPullRequestPayload pullRequest = PullRequestMocks.pullRequest(379, "Mobile menu",
                "feature/ACME-241-mobile-menu");
        when(githubClient.isEnabledFor(organizationId)).thenReturn(true);
        when(githubClient.findRepository(repository.githubRepositoryId())).thenReturn(Optional.of(repository));
        when(githubClient.getPullRequest(repository.fullName(), 379)).thenReturn(Optional.of(pullRequest));
    }

    private void reviewersEligible(User... reviewers) {
        when(organizationMemberQueryService.findActiveUserIdsByRoles(eq(organizationId), any()))
                .thenReturn(Set.of(Arrays.stream(reviewers).map(User::getId).toArray(UUID[]::new)));
        when(userQueryService.getUserEntitiesByIds(any())).thenReturn(List.of(reviewers));
    }

    private PullRequestShare storedShare() {
        PullRequestShare share = PullRequestMocks.share(author, organizationId);
        when(pullRequestShareRepository.findByIdAndOrganizationId(share.getId(), organizationId))
                .thenReturn(Optional.of(share));
        return share;
    }

    @Nested
    class CreateShare {

        @Test
        void validRequest_savesShareAndPostsOneEntryInThePullRequestChannel() {
            // Arrange
            githubReady();
            reviewersEligible(reviewerOne, reviewerTwo);
            when(chatChannelQueryService.findPullRequestChannelId(organizationId))
                    .thenReturn(Optional.of(channelId));

            // Act
            PullRequestShareResponse response = service.createShare(author, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.IN_REVIEW, "please look",
                            List.of(reviewerTwo.getId(), reviewerOne.getId())));

            // Assert
            assertThat(response.authorId()).isEqualTo(author.getId());
            assertThat(response.ticketKey()).isEqualTo("ACME-241");
            assertThat(response.note()).isEqualTo("please look");
            assertThat(response.reviewers()).extracting(reviewer -> reviewer.userId())
                    .containsExactly(reviewerTwo.getId(), reviewerOne.getId());
            ArgumentCaptor<ChatMessageCreateRequest> posted = ArgumentCaptor.forClass(ChatMessageCreateRequest.class);
            verify(chatMessageService).postMessage(eq(author), eq(organizationId), posted.capture(), eq(List.of()));
            assertThat(posted.getValue().channelId()).isEqualTo(channelId);
            assertThat(posted.getValue().pullRequestShareId()).isEqualTo(response.id());
            InOrder order = inOrder(pullRequestShareRepository, chatMessageService);
            order.verify(pullRequestShareRepository).saveAndFlush(any(PullRequestShare.class));
            order.verify(chatMessageService).postMessage(any(), any(), any(), any());
        }

        @Test
        void validRequest_readsGithubBeforeTheTransaction() {
            // Arrange
            githubReady();
            when(chatChannelQueryService.findPullRequestChannelId(organizationId))
                    .thenReturn(Optional.of(channelId));

            // Act
            service.createShare(author, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null, List.of()));

            // Assert
            InOrder order = inOrder(githubClient, transactionManager);
            order.verify(githubClient).getPullRequest(any(), eq(379));
            order.verify(transactionManager).getTransaction(any());
        }

        @Test
        void reviewerIsTheAuthor_throwsValidationAndTouchesNothing() {
            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null, List.of(author.getId()))))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_PULL_REQUEST_SHARE_INVALID.getCode());
            verifyNoInteractions(githubClient, chatMessageService);
            verify(pullRequestShareRepository, never()).saveAndFlush(any());
        }

        @Test
        void reviewerIsGuestOrInactive_throwsValidation() {
            // Arrange
            when(organizationMemberQueryService.findActiveUserIdsByRoles(eq(organizationId), any()))
                    .thenReturn(Set.of());

            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null, List.of(reviewerOne.getId()))))
                    .isInstanceOf(ValidationException.class);
            verifyNoInteractions(githubClient, chatMessageService);
        }

        @Test
        void duplicateReviewer_throwsValidation() {
            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null,
                            List.of(reviewerOne.getId(), reviewerOne.getId()))))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void elevenReviewers_throwsValidation() {
            // Arrange
            List<UUID> ids = Stream.generate(UUID::randomUUID).limit(11).toList();

            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null, ids)))
                    .isInstanceOf(ValidationException.class);
            verifyNoInteractions(githubClient);
        }

        @Test
        void relatedTickets_storesRowsInRequestOrderWithTheSnapshot() {
            // Arrange
            githubReady();
            Board board = PullRequestMocks.board("Fabrikam");
            Ticket first = PullRequestMocks.ticket(board, "ACME-1", "One");
            Ticket second = PullRequestMocks.ticket(board, "ACME-2", "Two");
            when(ticketQueryService.getAccessibleTickets(eq(author.getId()), eq(organizationId), any()))
                    .thenReturn(List.of(first, second));
            when(chatChannelQueryService.findPullRequestChannelId(organizationId))
                    .thenReturn(Optional.of(channelId));
            ArgumentCaptor<PullRequestShare> saved = ArgumentCaptor.forClass(PullRequestShare.class);

            // Act
            PullRequestShareResponse response = service.createShare(author, organizationId,
                    PullRequestMocks.createRequestWithTickets(List.of(second.getId(), first.getId())));

            // Assert
            verify(pullRequestShareRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getRelatedTickets()).extracting(PullRequestShareTicket::getTicketId)
                    .containsExactly(second.getId(), first.getId());
            assertThat(saved.getValue().getRelatedTickets()).extracting(PullRequestShareTicket::getPosition)
                    .containsExactly(0, 1);
            assertThat(saved.getValue().getRelatedTickets()).extracting(PullRequestShareTicket::getTicketTitle)
                    .containsExactly("Two", "One");
            assertThat(saved.getValue().getRelatedTickets()).extracting(PullRequestShareTicket::getBoardName)
                    .containsOnly("Fabrikam");
            assertThat(response.relatedTickets()).extracting(PullRequestShareTicketResponse::ticketKey)
                    .containsExactly("ACME-2", "ACME-1");
            assertThat(response.relatedTickets()).extracting(PullRequestShareTicketResponse::canOpen)
                    .containsOnly(true);
        }

        @Test
        void elevenRelatedTickets_throwsValidationNamingTheFieldBeforeGithub() {
            // Arrange
            List<UUID> ids = Stream.generate(UUID::randomUUID).limit(11).toList();

            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequestWithTickets(ids)))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_PULL_REQUEST_SHARE_INVALID.getCode());
            verifyNoInteractions(githubClient, chatMessageService);
            verify(pullRequestShareRepository, never()).saveAndFlush(any());
        }

        @Test
        void repeatedRelatedTicket_throwsValidationAndSavesNothing() {
            // Arrange
            UUID repeated = UUID.randomUUID();

            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequestWithTickets(List.of(repeated, repeated))))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_PULL_REQUEST_SHARE_INVALID.getCode());
            verifyNoInteractions(githubClient, chatMessageService);
            verify(pullRequestShareRepository, never()).saveAndFlush(any());
        }

        @Test
        void nullRelatedTicketId_throwsValidationAndSavesNothing() {
            // Arrange
            List<UUID> ids = Arrays.asList(UUID.randomUUID(), null);

            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequestWithTickets(ids)))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_PULL_REQUEST_SHARE_INVALID.getCode());
            verify(pullRequestShareRepository, never()).saveAndFlush(any());
        }

        @Test
        void inaccessibleRelatedTicket_throwsNotFoundAndSavesNothing() {
            // Arrange
            Ticket visible = PullRequestMocks.ticket(PullRequestMocks.board("Fabrikam"), "ACME-1", "One");
            UUID foreign = UUID.randomUUID();
            when(ticketQueryService.getAccessibleTickets(eq(author.getId()), eq(organizationId), any())).thenReturn(List.of(visible));

            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequestWithTickets(List.of(visible.getId(), foreign))))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_TICKET_FOUND.getCode());
            verifyNoInteractions(githubClient, chatMessageService);
            verify(pullRequestShareRepository, never()).saveAndFlush(any());
        }

        @Test
        void noteOf501Characters_throwsValidation() {
            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, "n".repeat(501), List.of())))
                    .isInstanceOf(ValidationException.class);
            verifyNoInteractions(githubClient);
        }

        @Test
        void githubNotConfiguredForOrganization_throwsNotFound() {
            // Arrange
            when(githubClient.isEnabledFor(organizationId)).thenReturn(false);

            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null, List.of())))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_GITHUB_NOT_CONFIGURED.getCode());
            verify(githubClient, never()).findRepository(anyLong());
        }

        @Test
        void repositoryOutsideInstallation_throwsNotFoundAndReadsNoPullRequest() {
            // Arrange
            when(githubClient.isEnabledFor(organizationId)).thenReturn(true);
            when(githubClient.findRepository(PullRequestMocks.GITHUB_REPOSITORY_ID)).thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null, List.of())))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_GITHUB_REPOSITORY_FOUND.getCode());
            verify(githubClient, never()).getPullRequest(any(), anyInt());
        }

        @Test
        void unknownPullRequest_throwsNotFound() {
            // Arrange
            when(githubClient.isEnabledFor(organizationId)).thenReturn(true);
            when(githubClient.findRepository(PullRequestMocks.GITHUB_REPOSITORY_ID))
                    .thenReturn(Optional.of(PullRequestMocks.repository()));
            when(githubClient.getPullRequest(PullRequestMocks.REPOSITORY_FULL_NAME, 379))
                    .thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null, List.of())))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_GITHUB_PULL_REQUEST_FOUND.getCode());
        }

        @Test
        void noPullRequestChannel_throwsValidationAndSavesNothing() {
            // Arrange
            githubReady();
            when(chatChannelQueryService.findPullRequestChannelId(organizationId)).thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null, List.of())))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_PULL_REQUEST_CHANNEL_ABSENT.getCode());
            verify(pullRequestShareRepository, never()).saveAndFlush(any());
            verifyNoInteractions(chatMessageService);
        }

        @Test
        void chatRefusesTheEntry_exceptionPropagatesSoTheTransactionRollsBack() {
            // Arrange
            githubReady();
            when(chatChannelQueryService.findPullRequestChannelId(organizationId))
                    .thenReturn(Optional.of(channelId));
            doThrow(new ValidationException(ExceptionDomainKazanError.VAL_PULL_REQUEST_SHARE_INVALID, Map.of()))
                    .when(chatMessageService).postMessage(any(), any(), any(), any());

            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null, List.of())))
                    .isInstanceOf(ValidationException.class);
            verify(transactionManager).rollback(any());
            verify(transactionManager, never()).commit(any());
        }

        @Test
        void reviewerAccountGoneAfterEligibilityCheck_throwsValidation() {
            // Arrange
            githubReady();
            when(organizationMemberQueryService.findActiveUserIdsByRoles(eq(organizationId), any()))
                    .thenReturn(Set.of(reviewerOne.getId()));
            when(userQueryService.getUserEntitiesByIds(any())).thenReturn(List.of());
            when(chatChannelQueryService.findPullRequestChannelId(organizationId))
                    .thenReturn(Optional.of(channelId));

            // Act and Assert
            assertThatThrownBy(() -> service.createShare(author, organizationId,
                    PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, null, List.of(reviewerOne.getId()))))
                    .isInstanceOf(ValidationException.class);
            verifyNoInteractions(chatMessageService);
        }
    }

    @Nested
    class EditShare {

        @Test
        void author_keepsKeptReviewersApprovalAndDropsRemovedOne() {
            // Arrange
            PullRequestShare share = storedShare();
            Instant approvedAt = Instant.parse("2026-10-27T10:00:00Z");
            PullRequestShareReviewer kept = PullRequestMocks.reviewer(share, reviewerOne, 0, approvedAt);
            PullRequestMocks.reviewer(share, reviewerTwo, 1, approvedAt);
            reviewersEligible(reviewerOne);

            // Act
            PullRequestShareResponse response = service.editShare(author, organizationId, share.getId(),
                    PullRequestMocks.updateRequest(PullRequestShareStatus.MERGED, "merged", List.of(reviewerOne.getId())));

            // Assert
            assertThat(response.reviewers()).hasSize(1);
            assertThat(response.reviewers().getFirst().userId()).isEqualTo(reviewerOne.getId());
            assertThat(response.reviewers().getFirst().approvedAt()).isEqualTo(approvedAt);
            assertThat(share.getReviewers()).containsExactly(kept);
            assertThat(share.getStatus()).isEqualTo(PullRequestShareStatus.MERGED);
            assertThat(share.getNote()).isEqualTo("merged");
            assertThat(share.getEditedAt()).isNotNull();
            verify(chatMessageService).touchPullRequestShareMessage(share,
                    Set.of(reviewerOne.getId(), reviewerTwo.getId()), true);
        }

        @Test
        void author_keepsStoredTicketWithoutAnAccessCheckAndKeepsItsSnapshot() {
            // Arrange
            PullRequestShare share = storedShare();
            Ticket kept = PullRequestMocks.ticket(PullRequestMocks.board("Fabrikam"), "ACME-1", "One");
            PullRequestShareTicket row = PullRequestMocks.ticketRow(share, kept, 0);
            kept.setTitle("Renamed since");
            when(ticketQueryService.getAccessibleTickets(eq(author.getId()), eq(organizationId), any())).thenReturn(List.of());

            // Act
            PullRequestShareResponse response = service.editShare(author, organizationId, share.getId(),
                    PullRequestMocks.updateRequestWithTickets(List.of(kept.getId())));

            // Assert
            assertThat(share.getRelatedTickets()).containsExactly(row);
            assertThat(row.getTicketTitle()).isEqualTo("One");
            assertThat(response.relatedTickets()).singleElement().satisfies(ticket -> {
                assertThat(ticket.ticketTitle()).isEqualTo("One");
                assertThat(ticket.canOpen()).as("the author lost access, the row stays").isFalse();
            });
        }

        @Test
        void author_addsReordersAndRemovesRelatedTickets() {
            // Arrange
            PullRequestShare share = storedShare();
            Board board = PullRequestMocks.board("Fabrikam");
            Ticket kept = PullRequestMocks.ticket(board, "ACME-1", "One");
            Ticket removed = PullRequestMocks.ticket(board, "ACME-2", "Two");
            Ticket added = PullRequestMocks.ticket(board, "ACME-3", "Three");
            PullRequestShareTicket keptRow = PullRequestMocks.ticketRow(share, kept, 0);
            PullRequestMocks.ticketRow(share, removed, 1);
            when(ticketQueryService.getAccessibleTickets(eq(author.getId()), eq(organizationId), any()))
                    .thenReturn(List.of(added, kept));

            // Act
            PullRequestShareResponse response = service.editShare(author, organizationId, share.getId(),
                    PullRequestMocks.updateRequestWithTickets(List.of(added.getId(), kept.getId())));

            // Assert
            assertThat(share.getRelatedTickets()).extracting(PullRequestShareTicket::getTicketId)
                    .containsExactly(added.getId(), kept.getId());
            assertThat(share.getRelatedTickets().get(1)).isSameAs(keptRow);
            assertThat(keptRow.getPosition()).isEqualTo(1);
            assertThat(share.getRelatedTickets().get(0).getTicketKey()).isEqualTo("ACME-3");
            assertThat(response.relatedTickets()).extracting(PullRequestShareTicketResponse::ticketKey)
                    .containsExactly("ACME-3", "ACME-1");
        }

        @Test
        void author_inaccessibleAddedTicket_throwsNotFoundAndKeepsTheStoredRows() {
            // Arrange
            PullRequestShare share = storedShare();
            Ticket kept = PullRequestMocks.ticket(PullRequestMocks.board("Fabrikam"), "ACME-1", "One");
            PullRequestMocks.ticketRow(share, kept, 0);
            UUID foreign = UUID.randomUUID();
            when(ticketQueryService.getAccessibleTickets(eq(author.getId()), eq(organizationId), any())).thenReturn(List.of());

            // Act and Assert
            assertThatThrownBy(() -> service.editShare(author, organizationId, share.getId(),
                    PullRequestMocks.updateRequestWithTickets(List.of(kept.getId(), foreign))))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_TICKET_FOUND.getCode());
            assertThat(share.getRelatedTickets()).extracting(PullRequestShareTicket::getTicketId)
                    .containsExactly(kept.getId());
            verify(chatMessageService, never()).touchPullRequestShareMessage(any(), any(), anyBoolean());
        }

        @Test
        void author_repeatedRelatedTicket_throwsValidation() {
            // Arrange
            PullRequestShare share = storedShare();
            UUID repeated = UUID.randomUUID();

            // Act and Assert
            assertThatThrownBy(() -> service.editShare(author, organizationId, share.getId(),
                    PullRequestMocks.updateRequestWithTickets(List.of(repeated, repeated))))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.VAL_PULL_REQUEST_SHARE_INVALID.getCode());
        }

        @Test
        void author_elevenRelatedTickets_throwsValidation() {
            // Arrange
            PullRequestShare share = storedShare();
            List<UUID> ids = Stream.generate(UUID::randomUUID).limit(11).toList();

            // Act and Assert
            assertThatThrownBy(() -> service.editShare(author, organizationId, share.getId(),
                    PullRequestMocks.updateRequestWithTickets(ids)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void author_passesTheReviewersBeforeTheEditSoGainedAndLostAreNotified() {
            // Arrange
            PullRequestShare share = storedShare();
            PullRequestMocks.reviewer(share, reviewerOne, 0, null);
            reviewersEligible(reviewerTwo);

            // Act
            service.editShare(author, organizationId, share.getId(),
                    PullRequestMocks.updateRequest(PullRequestShareStatus.OPEN, null, List.of(reviewerTwo.getId())));

            // Assert
            ArgumentCaptor<Set<UUID>> before = ArgumentCaptor.forClass(Set.class);
            verify(chatMessageService).touchPullRequestShareMessage(eq(share), before.capture(), eq(true));
            assertThat(before.getValue()).containsExactly(reviewerOne.getId());
            assertThat(share.getReviewers()).extracting(row -> row.getUser().getId())
                    .containsExactly(reviewerTwo.getId());
        }

        @Test
        void notTheAuthor_throwsForbiddenAndChangesNothing() {
            // Arrange
            PullRequestShare share = storedShare();

            // Act and Assert
            assertThatThrownBy(() -> service.editShare(reviewerOne, organizationId, share.getId(),
                    PullRequestMocks.updateRequest(PullRequestShareStatus.MERGED, null, List.of())))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.SEC_CANNOT_EDIT_OTHERS_SHARE.getCode());
            assertThat(share.getStatus()).isEqualTo(PullRequestShareStatus.IN_REVIEW);
            verifyNoInteractions(chatMessageService);
        }

        @Test
        void shareOfAnotherOrganization_throwsNotFound() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            when(pullRequestShareRepository.findByIdAndOrganizationId(shareId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.editShare(author, organizationId, shareId,
                    PullRequestMocks.updateRequest(PullRequestShareStatus.MERGED, null, List.of())))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.NOT_PULL_REQUEST_SHARE_FOUND.getCode());
        }

        @Test
        void authorNamedAsReviewer_throwsValidation() {
            // Arrange
            PullRequestShare share = storedShare();

            // Act and Assert
            assertThatThrownBy(() -> service.editShare(author, organizationId, share.getId(),
                    PullRequestMocks.updateRequest(PullRequestShareStatus.OPEN, null, List.of(author.getId()))))
                    .isInstanceOf(ValidationException.class);
            verifyNoInteractions(chatMessageService);
        }
    }

    @Nested
    class DeleteShare {

        @Test
        void author_marksShareDeletedWithoutCascadeAndRemovesItsEntry() {
            // Arrange
            PullRequestShare share = storedShare();

            // Act
            service.deleteShare(author, organizationId, share.getId());

            // Assert
            verify(pullRequestShareRepository).deleteByIdWithoutRelations(share.getId());
            verify(pullRequestShareRepository, never()).delete(any(PullRequestShare.class));
            verify(chatMessageService).removePullRequestShareMessage(share);
        }

        @Test
        void notTheAuthor_throwsForbidden() {
            // Arrange
            PullRequestShare share = storedShare();

            // Act and Assert
            assertThatThrownBy(() -> service.deleteShare(reviewerOne, organizationId, share.getId()))
                    .isInstanceOf(ForbiddenException.class);
            verify(pullRequestShareRepository, never()).deleteByIdWithoutRelations(any());
            verifyNoInteractions(chatMessageService);
        }

        @Test
        void shareOfAnotherOrganization_throwsNotFound() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            when(pullRequestShareRepository.findByIdAndOrganizationId(shareId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.deleteShare(author, organizationId, shareId))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class SetApproval {

        private PullRequestShareReviewer reviewerRow(PullRequestShare share, Instant approvedAt) {
            PullRequestShareReviewer row = PullRequestMocks.reviewer(share, reviewerOne, 0, approvedAt);
            when(pullRequestShareReviewerRepository.findByShareIdAndUserId(share.getId(), reviewerOne.getId()))
                    .thenReturn(Optional.of(row));
            return row;
        }

        @Test
        void reviewerApproves_setsApprovedAtAndTouchesTheEntryWithoutEdit() {
            // Arrange
            PullRequestShare share = storedShare();
            PullRequestShareReviewer row = reviewerRow(share, null);

            // Act
            PullRequestShareResponse response = service.setApproval(reviewerOne, organizationId, share.getId(), true);

            // Assert
            assertThat(row.getApprovedAt()).isNotNull();
            assertThat(response.reviewers().getFirst().approvedAt()).isEqualTo(row.getApprovedAt());
            verify(chatMessageService).touchPullRequestShareMessage(share, Set.of(reviewerOne.getId()), false);
        }

        @Test
        void reviewerWithdraws_clearsApprovedAt() {
            // Arrange
            PullRequestShare share = storedShare();
            PullRequestShareReviewer row = reviewerRow(share, Instant.parse("2026-10-27T10:00:00Z"));

            // Act
            service.setApproval(reviewerOne, organizationId, share.getId(), false);

            // Assert
            assertThat(row.getApprovedAt()).isNull();
            verify(chatMessageService).touchPullRequestShareMessage(eq(share), any(), eq(false));
        }

        @Test
        void approvingTwice_isIdempotentAndKeepsTheFirstTimeAndTouchesNothing() {
            // Arrange
            PullRequestShare share = storedShare();
            Instant firstApproval = Instant.parse("2026-10-27T10:00:00Z");
            PullRequestShareReviewer row = reviewerRow(share, firstApproval);

            // Act
            PullRequestShareResponse response = service.setApproval(reviewerOne, organizationId, share.getId(), true);

            // Assert
            assertThat(row.getApprovedAt()).isEqualTo(firstApproval);
            assertThat(response.reviewers().getFirst().approvedAt()).isEqualTo(firstApproval);
            verify(chatMessageService, never()).touchPullRequestShareMessage(any(), any(), anyBoolean());
        }

        @Test
        void withdrawingWithoutApproval_isIdempotent() {
            // Arrange
            PullRequestShare share = storedShare();
            reviewerRow(share, null);

            // Act
            service.setApproval(reviewerOne, organizationId, share.getId(), false);

            // Assert
            verify(chatMessageService, never()).touchPullRequestShareMessage(any(), any(), anyBoolean());
        }

        @Test
        void notAReviewer_throwsForbidden() {
            // Arrange
            PullRequestShare share = storedShare();
            when(pullRequestShareReviewerRepository.findByShareIdAndUserId(share.getId(), author.getId()))
                    .thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.setApproval(author, organizationId, share.getId(), true))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainKazanError.SEC_NOT_PULL_REQUEST_REVIEWER.getCode());
            verifyNoInteractions(chatMessageService);
        }

        @Test
        void shareOfAnotherOrganization_throwsNotFoundBeforeAnyReviewerCheck() {
            // Arrange
            UUID shareId = UUID.randomUUID();
            when(pullRequestShareRepository.findByIdAndOrganizationId(shareId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and Assert
            assertThatThrownBy(() -> service.setApproval(reviewerOne, organizationId, shareId, true))
                    .isInstanceOf(NotFoundException.class);
            verifyNoInteractions(pullRequestShareReviewerRepository);
        }
    }
}
