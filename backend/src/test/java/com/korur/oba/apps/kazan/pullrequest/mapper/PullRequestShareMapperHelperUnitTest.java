package com.korur.oba.apps.kazan.pullrequest.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestSharePreviewResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareTicketResponse;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShareReviewer;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShareTicket;
import com.korur.oba.apps.kazan.pullrequest.helper.PullRequestShareSummaryRenderer;
import com.korur.oba.apps.kazan.pullrequest.mock_data.PullRequestMocks;
import com.korur.oba.apps.kazan.pullrequest.validator.PullRequestShareValidator;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.service.TicketQueryService;
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class PullRequestShareMapperHelperUnitTest {

    @Mock
    private TicketQueryService ticketQueryService;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;
    @Mock
    private PullRequestShareValidator pullRequestShareValidator;

    private PullRequestShareMapperHelper helper;

    private final UUID organizationId = UUID.randomUUID();
    private final User author = PullRequestMocks.user("Ada", "Lovelace");
    private final User keptReviewer = PullRequestMocks.user("Alan", "Turing");
    private final User removedReviewer = PullRequestMocks.user("Grace", "Hopper");
    private final User addedReviewer = PullRequestMocks.user("Linus", "Torvalds");

    @BeforeEach
    void setUp() {
        helper = new PullRequestShareMapperHelper(new PullRequestShareMapperImpl(), ticketQueryService,
                new PullRequestShareSummaryRenderer(), chatChannelQueryService, pullRequestShareValidator);
    }

    @Test
    void setCreateRelations_reviewers_setsAuthorAndUnapprovedRowsInOrder() {
        // Arrange
        PullRequestShare share = PullRequestMocks.share(null, organizationId);

        // Act
        helper.setCreateRelations(share, author, List.of(keptReviewer, addedReviewer), List.of());

        // Assert
        assertThat(share.getAuthor()).isSameAs(author);
        assertThat(share.getReviewers()).extracting(PullRequestShareReviewer::getPosition).containsExactly(0, 1);
        assertThat(share.getReviewers()).extracting(row -> row.getUser().getId())
                .containsExactly(keptReviewer.getId(), addedReviewer.getId());
        assertThat(share.getReviewers()).allSatisfy(row -> {
            assertThat(row.getApprovedAt()).isNull();
            assertThat(row.getShare()).isSameAs(share);
        });
    }

    @Test
    void setUpdateRelations_keptRemovedAndAddedReviewers_keepsApprovalOfKeptAndDropsRemoved() {
        // Arrange
        PullRequestShare share = PullRequestMocks.share(author, organizationId);
        Instant approvedAt = Instant.parse("2026-10-27T10:00:00Z");
        PullRequestShareReviewer kept = PullRequestMocks.reviewer(share, keptReviewer, 1, approvedAt);
        PullRequestMocks.reviewer(share, removedReviewer, 0, approvedAt);

        // Act
        helper.setUpdateRelations(share, List.of(addedReviewer, keptReviewer), List.of(), Map.of());

        // Assert
        assertThat(share.getReviewers()).extracting(row -> row.getUser().getId())
                .containsExactly(addedReviewer.getId(), keptReviewer.getId());
        assertThat(share.getReviewers().get(1)).as("the kept reviewer keeps the same row").isSameAs(kept);
        assertThat(kept.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(kept.getPosition()).isEqualTo(1);
        assertThat(share.getReviewers().get(0).getApprovedAt()).as("an added reviewer starts unapproved").isNull();
        assertThat(share.getReviewers().get(0).getShare()).isSameAs(share);
    }

    @Test
    void getReviewerIds_share_listsUserIdsInDisplayOrder() {
        // Arrange
        PullRequestShare share = PullRequestMocks.share(author, organizationId);
        PullRequestMocks.reviewer(share, keptReviewer, 0, null);
        PullRequestMocks.reviewer(share, addedReviewer, 1, null);

        // Act and Assert
        assertThat(helper.getReviewerIds(share)).containsExactly(keptReviewer.getId(), addedReviewer.getId());
    }

    @Test
    void toResponse_readerCanOpenTicket_linksTicketAndRendersSummary() {
        // Arrange
        PullRequestShare share = PullRequestMocks.share(author, organizationId);
        share.setNote("for prod");
        PullRequestMocks.reviewer(share, keptReviewer, 0, Instant.parse("2026-10-27T10:00:00Z"));
        Ticket ticket = Ticket.builder().key("ACME-241").build();
        ticket.setId(UUID.randomUUID());
        Board board = Board.builder().build();
        board.setId(UUID.randomUUID());
        ticket.setBoard(board);
        when(ticketQueryService.getAccessibleTicketsByKeys(any(), eq(organizationId), any())).thenReturn(List.of(ticket));

        // Act
        PullRequestShareResponse response = helper.toResponse(share, keptReviewer.getId());

        // Assert
        assertThat(response.linkedTicket()).isNotNull();
        assertThat(response.linkedTicket().ticketKey()).isEqualTo("ACME-241");
        assertThat(response.linkedTicket().ticketId()).isEqualTo(ticket.getId());
        assertThat(response.linkedTicket().boardId()).isEqualTo(board.getId());
        assertThat(response.summaryText()).isEqualTo(
                "ACME-241: Mobile menu\nhttps://github.com/senseylabs/fabrikam/pull/379\nStatus: In Review for prod");
        assertThat(response.reviewers()).hasSize(1);
        assertThat(response.reviewers().getFirst().approvedAt()).isNotNull();
        assertThat(response.url()).isEqualTo("https://github.com/senseylabs/fabrikam/pull/379");
    }

    @Test
    void toResponse_ticketOnBoardOfNoMembership_leavesKeyAsPlainTextWithNoLink() {
        // Arrange
        PullRequestShare share = PullRequestMocks.share(author, organizationId);
        when(ticketQueryService.getAccessibleTicketsByKeys(any(), eq(organizationId), any())).thenReturn(List.of());

        // Act
        PullRequestShareResponse response = helper.toResponse(share, keptReviewer.getId());

        // Assert
        assertThat(response.linkedTicket()).isNull();
        assertThat(response.ticketKey()).isEqualTo("ACME-241");
        assertThat(response.summaryText()).startsWith("ACME-241: Mobile menu");
    }

    @Test
    void toResponses_manyShares_resolvesTicketsInOneQuery() {
        // Arrange
        PullRequestShare first = PullRequestMocks.share(author, organizationId);
        PullRequestShare second = PullRequestMocks.share(author, organizationId);
        when(ticketQueryService.getAccessibleTicketsByKeys(any(), eq(organizationId), any())).thenReturn(List.of());

        // Act
        List<PullRequestShareResponse> responses = helper.toResponses(List.of(first, second), organizationId, keptReviewer.getId());

        // Assert
        assertThat(responses).extracting(PullRequestShareResponse::id).containsExactly(first.getId(),
                second.getId());
        verify(ticketQueryService).getAccessibleTicketsByKeys(any(), eq(organizationId), any());
    }

    @Test
    void toResponses_noReader_resolvesNoTicket() {
        // Arrange
        PullRequestShare share = PullRequestMocks.share(author, organizationId);

        // Act
        List<PullRequestShareResponse> responses = helper.toResponses(List.of(share), organizationId, null);

        // Assert
        assertThat(responses.getFirst().linkedTicket()).isNull();
        verify(ticketQueryService, never()).getAccessibleTicketsByKeys(any(), eq(organizationId), any());
    }

    @Test
    void toResponses_noShares_returnsEmptyWithoutQuery() {
        // Act and Assert
        assertThat(helper.toResponses(List.of(), organizationId, UUID.randomUUID())).isEmpty();
        verify(ticketQueryService, never()).getAccessibleTicketsByKeys(any(), eq(organizationId), any());
    }

    @Test
    void toPullRequestResponse_branchWithoutKey_hasNoTicketLookup() {
        // Act and Assert
        assertThat(helper.toPullRequestResponse(PullRequestMocks.pullRequest(5, "plain", "feature/menu"),
                organizationId,
                keptReviewer.getId()).ticketKey()).isNull();
        verify(ticketQueryService, never()).getAccessibleTicketsByKeys(any(), eq(organizationId), any());
    }

    @Nested
    class RelatedTickets {

        private final Board board = PullRequestMocks.board("Fabrikam");
        private final Ticket first = PullRequestMocks.ticket(board, "ACME-1", "One");
        private final Ticket second = PullRequestMocks.ticket(board, "ACME-2", "Two");
        private final Ticket third = PullRequestMocks.ticket(board, "ACME-3", "Three");

        @Test
        void setCreateRelations_tickets_storesRowsInRequestOrderWithTheSnapshot() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(null, organizationId);

            // Act
            helper.setCreateRelations(share, author, List.of(), List.of(second, first));

            // Assert
            assertThat(share.getRelatedTickets()).extracting(PullRequestShareTicket::getTicketId)
                    .containsExactly(second.getId(), first.getId());
            assertThat(share.getRelatedTickets()).extracting(PullRequestShareTicket::getPosition)
                    .containsExactly(0, 1);
            assertThat(share.getRelatedTickets()).extracting(PullRequestShareTicket::getTicketKey)
                    .containsExactly("ACME-2", "ACME-1");
            assertThat(share.getRelatedTickets()).allSatisfy(row -> {
                assertThat(row.getShare()).isSameAs(share);
                assertThat(row.getBoardId()).isEqualTo(board.getId());
                assertThat(row.getBoardName()).isEqualTo("Fabrikam");
            });
        }

        @Test
        void resolveRelatedTickets_tickets_returnsThemInRequestOrderFromOneQuery() {
            // Arrange
            when(ticketQueryService.getAccessibleTickets(eq(author.getId()), eq(organizationId), any()))
                    .thenReturn(List.of(first, second, third));

            // Act
            List<Ticket> tickets = helper.resolveRelatedTickets(author.getId(),
                    organizationId,
                    List.of(third.getId(), first.getId(), second.getId()));

            // Assert
            assertThat(tickets).containsExactly(third, first, second);
            verify(ticketQueryService, times(1)).getAccessibleTickets(any(), eq(organizationId), any());
            verify(pullRequestShareValidator).ensureRelatedTicketsAccessible(any(), any());
        }

        @Test
        void resolveAddedTickets_noIds_returnsEmptyWithoutQuery() {
            // Act and Assert
            assertThat(helper.resolveAddedTickets(author.getId(), organizationId, List.of())).isEmpty();
            verifyNoInteractions(ticketQueryService, pullRequestShareValidator);
        }

        @Test
        void resolveAddedTickets_inaccessibleTicket_propagatesTheValidatorRefusal() {
            // Arrange
            when(ticketQueryService.getAccessibleTickets(eq(author.getId()), eq(organizationId), any())).thenReturn(List.of());
            doThrow(new NotFoundException(ExceptionDomainKazanError.NOT_TICKET_FOUND, Map.of()))
                    .when(pullRequestShareValidator).ensureRelatedTicketsAccessible(any(), any());

            // Act and Assert
            assertThatThrownBy(() -> helper.resolveAddedTickets(author.getId(), organizationId, List.of(first.getId())))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void findAddedTicketIds_storedAndNewIds_returnsOnlyTheNewOnesInEditOrder() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            PullRequestMocks.ticketRow(share, first, 0);

            // Act
            List<UUID> added = helper.findAddedTicketIds(share,
                    List.of(third.getId(), first.getId(), second.getId()));

            // Assert
            assertThat(added).containsExactly(third.getId(), second.getId());
        }

        @Test
        void setUpdateRelations_keptAddedAndLeftOutTickets_keepsKeptRowAndSnapshotAddsAndRemoves() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            PullRequestShareTicket kept = PullRequestMocks.ticketRow(share, first, 0);
            PullRequestMocks.ticketRow(share, second, 1);
            first.setTitle("Renamed since");

            // Act
            helper.setUpdateRelations(share, List.of(), List.of(third.getId(), first.getId()),
                    Map.of(third.getId(), third));

            // Assert
            assertThat(share.getRelatedTickets()).extracting(PullRequestShareTicket::getTicketId)
                    .containsExactly(third.getId(), first.getId());
            assertThat(share.getRelatedTickets().get(1)).as("the kept ticket keeps the same row").isSameAs(kept);
            assertThat(kept.getPosition()).isEqualTo(1);
            assertThat(kept.getTicketTitle()).as("the kept snapshot is unchanged").isEqualTo("One");
            assertThat(share.getRelatedTickets().get(0).getTicketKey()).isEqualTo("ACME-3");
            assertThat(share.getRelatedTickets().get(0).getPosition()).isZero();
            assertThat(share.getRelatedTickets().get(0).getShare()).isSameAs(share);
            verifyNoInteractions(ticketQueryService, pullRequestShareValidator);
        }

        @Test
        void setUpdateRelations_reorderedKeptTickets_movesRowsWithoutNewOnes() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            PullRequestShareTicket one = PullRequestMocks.ticketRow(share, first, 0);
            PullRequestShareTicket two = PullRequestMocks.ticketRow(share, second, 1);

            // Act
            helper.setUpdateRelations(share, List.of(), List.of(second.getId(), first.getId()), Map.of());

            // Assert
            assertThat(share.getRelatedTickets()).containsExactly(two, one);
            assertThat(two.getPosition()).isZero();
            assertThat(one.getPosition()).isEqualTo(1);
        }

        @Test
        void setUpdateRelations_emptyList_removesEveryRow() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            PullRequestMocks.ticketRow(share, first, 0);

            // Act
            helper.setUpdateRelations(share, List.of(), List.of(), Map.of());

            // Assert
            assertThat(share.getRelatedTickets()).isEmpty();
        }

        @Test
        void toResponses_manySharesWithTickets_makesOneAccessibleTicketsCallForThePage() {
            // Arrange
            PullRequestShare one = PullRequestMocks.share(author, organizationId);
            PullRequestMocks.ticketRow(one, first, 0);
            PullRequestMocks.ticketRow(one, second, 1);
            PullRequestShare two = PullRequestMocks.share(author, organizationId);
            PullRequestMocks.ticketRow(two, second, 0);
            PullRequestMocks.ticketRow(two, third, 1);
            when(ticketQueryService.getAccessibleTickets(eq(keptReviewer.getId()), eq(organizationId), any()))
                    .thenReturn(List.of(first, second));

            // Act
            List<PullRequestShareResponse> responses = helper.toResponses(List.of(one, two), organizationId, keptReviewer.getId());

            // Assert
            verify(ticketQueryService, times(1)).getAccessibleTickets(any(), eq(organizationId), any());
            ArgumentCaptor<List<UUID>> ids = ArgumentCaptor.forClass(List.class);
            verify(ticketQueryService).getAccessibleTickets(eq(keptReviewer.getId()), eq(organizationId), ids.capture());
            assertThat(ids.getValue()).containsExactlyInAnyOrder(first.getId(), second.getId(), third.getId());
            assertThat(responses.get(0).relatedTickets()).extracting(PullRequestShareTicketResponse::ticketKey)
                    .containsExactly("ACME-1", "ACME-2");
            assertThat(responses.get(0).relatedTickets()).extracting(PullRequestShareTicketResponse::canOpen)
                    .containsExactly(true, true);
            assertThat(responses.get(1).relatedTickets()).extracting(PullRequestShareTicketResponse::canOpen)
                    .containsExactly(true, false);
        }

        @Test
        void toResponses_readerOffTheBoard_marksEveryTicketNotOpenable() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            PullRequestMocks.ticketRow(share, first, 0);
            when(ticketQueryService.getAccessibleTickets(eq(keptReviewer.getId()), eq(organizationId), any())).thenReturn(List.of());

            // Act
            PullRequestShareResponse response = helper.toResponse(share, keptReviewer.getId());

            // Assert
            assertThat(response.relatedTickets()).singleElement().satisfies(ticket -> {
                assertThat(ticket.canOpen()).isFalse();
                assertThat(ticket.ticketKey()).isEqualTo("ACME-1");
                assertThat(ticket.boardName()).isEqualTo("Fabrikam");
            });
        }

        @Test
        void toResponses_nullReader_marksEveryTicketNotOpenableWithoutAQuery() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            PullRequestMocks.ticketRow(share, first, 0);

            // Act
            PullRequestShareResponse response = helper.toResponse(share, null);

            // Assert
            assertThat(response.relatedTickets()).singleElement()
                    .satisfies(ticket -> assertThat(ticket.canOpen()).isFalse());
            verify(ticketQueryService, never()).getAccessibleTickets(any(), eq(organizationId), any());
        }

        @Test
        void toResponses_sharesWithoutTickets_makesNoAccessibleTicketsCall() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);

            // Act
            PullRequestShareResponse response = helper.toResponse(share, keptReviewer.getId());

            // Assert
            assertThat(response.relatedTickets()).isEmpty();
            verify(ticketQueryService, never()).getAccessibleTickets(any(), eq(organizationId), any());
        }

        @Test
        void toResponse_withRelatedTickets_summaryTextIsUnchanged() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            share.setNote("for prod");
            String without = helper.toResponse(share, null).summaryText();
            PullRequestMocks.ticketRow(share, first, 0);

            // Act
            PullRequestShareResponse response = helper.toResponse(share, null);

            // Assert
            assertThat(response.summaryText()).isEqualTo(without);
            assertThat(response.summaryText()).doesNotContain("ACME-1");
        }
    }

    @Nested
    class ChatEntryAndAuthorFields {

        @Test
        void toResponses_shareWithLiveChatEntry_setsAuthorNamesChannelAndMessage() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            ChatMessage entry = ChatMocks.pullRequestShareMessage(author, organizationId, share.getId());
            when(chatChannelQueryService.findPullRequestShareMessages(List.of(share.getId())))
                    .thenReturn(Map.of(share.getId(), entry));

            // Act
            PullRequestShareResponse response = helper.toResponses(List.of(share), organizationId, keptReviewer.getId()).getFirst();

            // Assert
            assertThat(response.authorFirstName()).isEqualTo("Ada");
            assertThat(response.authorLastName()).isEqualTo("Lovelace");
            assertThat(response.channelId()).isEqualTo(entry.getChannelId());
            assertThat(response.messageId()).isEqualTo(entry.getId());
        }

        @Test
        void toResponses_shareWithoutLiveChatEntry_leavesChannelAndMessageNull() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);

            // Act
            PullRequestShareResponse response = helper.toResponses(List.of(share), organizationId, keptReviewer.getId()).getFirst();

            // Assert
            assertThat(response.channelId()).isNull();
            assertThat(response.messageId()).isNull();
            assertThat(response.id()).isEqualTo(share.getId());
        }

        @Test
        void toResponses_manyShares_looksUpTheChatEntriesOnceAndMatchesEachShare() {
            // Arrange
            PullRequestShare withEntry = PullRequestMocks.share(author, organizationId);
            PullRequestShare withoutEntry = PullRequestMocks.share(author, organizationId);
            ChatMessage entry = ChatMocks.pullRequestShareMessage(author, organizationId, withEntry.getId());
            when(chatChannelQueryService.findPullRequestShareMessages(any()))
                    .thenReturn(Map.of(withEntry.getId(), entry));

            // Act
            List<PullRequestShareResponse> responses = helper.toResponses(List.of(withEntry, withoutEntry),
                    organizationId,
                    keptReviewer.getId());

            // Assert
            assertThat(responses.get(0).messageId()).isEqualTo(entry.getId());
            assertThat(responses.get(1).messageId()).isNull();
            verify(chatChannelQueryService, times(1)).findPullRequestShareMessages(any());
        }

        @Test
        void toResponses_noShares_looksUpNoChatEntry() {
            // Act
            List<PullRequestShareResponse> responses = helper.toResponses(List.of(), organizationId, keptReviewer.getId());

            // Assert
            assertThat(responses).isEmpty();
            verifyNoInteractions(chatChannelQueryService);
        }
    }

    @Nested
    class ToResponsePage {

        @Test
        void page_keepsThePagingMetadataAndMapsTheContent() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            Pageable pageable = PageRequest.of(1, 5);
            Page<PullRequestShare> page = new PageImpl<>(List.of(share), pageable, 6);

            // Act
            Page<PullRequestShareResponse> result = helper.toResponsePage(page, organizationId, keptReviewer.getId());

            // Assert
            assertThat(result.getContent()).extracting(PullRequestShareResponse::id).containsExactly(share.getId());
            assertThat(result.getTotalElements()).isEqualTo(6);
            assertThat(result.getNumber()).isEqualTo(1);
            assertThat(result.getSize()).isEqualTo(5);
        }

        @Test
        void emptyPage_returnsAnEmptyPage() {
            // Act
            Page<PullRequestShareResponse> result = helper.toResponsePage(Page.empty(), organizationId, keptReviewer.getId());

            // Assert
            assertThat(result.isEmpty()).isTrue();
        }
    }

    @Nested
    class ToPreviewResponse {

        @Test
        void unsavedShare_returnsThePullRequestAndTheTextOfTheSavedCard() {
            // Arrange
            PullRequestShare share = PullRequestMocks.share(author, organizationId);
            share.setNote("for prod");
            GithubPullRequestPayload pullRequest = PullRequestMocks.pullRequest(379, "Mobile menu",
                    "feature/ACME-241-mobile-menu");

            // Act
            PullRequestSharePreviewResponse preview = helper.toPreviewResponse(share, pullRequest,
                    keptReviewer.getId());

            // Assert
            assertThat(preview.pullRequest().number()).isEqualTo(379);
            assertThat(preview.pullRequest().ticketKey()).isEqualTo("ACME-241");
            assertThat(preview.summaryText()).isEqualTo(helper.toResponse(share, keptReviewer.getId()).summaryText());
            assertThat(preview.summaryText()).isEqualTo(
                    "ACME-241: Mobile menu\nhttps://github.com/senseylabs/fabrikam/pull/379\nStatus: In Review for prod");
        }
    }
}
