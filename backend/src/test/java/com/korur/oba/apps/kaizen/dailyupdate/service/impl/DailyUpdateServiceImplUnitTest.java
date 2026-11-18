package com.korur.oba.apps.kaizen.dailyupdate.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.springframework.data.jpa.domain.Specification;

// 3. Java SDK
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatMessageCreateRequest;
import com.korur.oba.apps.kaizen.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kaizen.chat.service.ChatMessageService;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdateCreateRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdateTicketRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdateUpdateRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateResponse;
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdateTicket;
import com.korur.oba.apps.kaizen.dailyupdate.helper.DailyUpdateDeadlineResolver;
import com.korur.oba.apps.kaizen.dailyupdate.helper.DailyUpdateSummaryRenderer;
import com.korur.oba.apps.kaizen.dailyupdate.mapper.DailyUpdateMapperHelper;
import com.korur.oba.apps.kaizen.dailyupdate.mapper.DailyUpdateMapperImpl;
import com.korur.oba.apps.kaizen.dailyupdate.mock_data.DailyUpdateMocks;
import com.korur.oba.apps.kaizen.dailyupdate.repository.DailyUpdateRepository;
import com.korur.oba.apps.kaizen.dailyupdate.validator.DailyUpdateValidator;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.mapper.TicketEmbeddedMapperImpl;
import com.korur.oba.apps.kaizen.ticket.service.TicketQueryService;
import com.korur.oba.apps.kaizen.ticket.service.TicketService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.time.BusinessCalendar;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.service.OrganizationAccessService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class DailyUpdateServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private DailyUpdateRepository dailyUpdateRepository;
    @Mock
    private TicketQueryService ticketQueryService;
    @Mock
    private BoardMemberQueryService boardMemberQueryService;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;
    @Mock
    private OrganizationAccessService organizationAccessService;
    @Mock
    private AppAccessQueryService appAccessQueryService;
    @Mock
    private TicketService ticketService;
    @Mock
    private ChatMessageService chatMessageService;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;

    private DailyUpdateServiceImpl service;
    private User user;
    private Board board;
    private final List<Ticket> accessibleTickets = new ArrayList<>();

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        DailyUpdateValidator validator = new DailyUpdateValidator(dailyUpdateRepository, loggingService);
        DailyUpdateMapperImpl mapper = new DailyUpdateMapperImpl(new TicketEmbeddedMapperImpl());
        DailyUpdateMapperHelper mapperHelper = new DailyUpdateMapperHelper(
                ticketQueryService, mapper, new DailyUpdateSummaryRenderer(), validator, organizationMemberQueryService);
        Clock clock = Clock.systemUTC();
        BusinessCalendar businessCalendar = new BusinessCalendar(clock);
        DailyUpdateQueryServiceImpl queryService = new DailyUpdateQueryServiceImpl(
                dailyUpdateRepository, validator, mapper, mapperHelper,
                new DailyUpdateDeadlineResolver(validator, businessCalendar, clock), ticketQueryService,
                boardMemberQueryService, organizationMemberQueryService, organizationAccessService,
                appAccessQueryService, businessCalendar);
        service = new DailyUpdateServiceImpl(dailyUpdateRepository, queryService, validator, mapper, mapperHelper,
                ticketService, organizationMemberQueryService, chatMessageService,
                chatChannelQueryService, loggingService);

        user = DailyUpdateMocks.user();
        board = DailyUpdateMocks.board("Fabrikam Board");
        // The author's primary organization: updates posted without a header land there, and only
        // tickets of that organization are reachable.
        lenient().when(organizationMemberQueryService.findPrimaryActiveOrganizationId(user.getId()))
                .thenReturn(Optional.of(organizationId));
        lenient().when(organizationMemberQueryService.findMember(organizationId, user.getId()))
                .thenReturn(Optional.of(DailyUpdateMocks.membership(OrganizationStatus.ACTIVE,
                        OrganizationMemberStatus.ACTIVE)));
    }

    /**
     * Makes exactly the given tickets reachable for the caller, as the real query would for
     * non-deleted tickets on member boards, and the caller a contributor on their boards.
     */
    private void givenAccessible(Ticket... tickets) {
        accessibleTickets.clear();
        accessibleTickets.addAll(List.of(tickets));
        lenient().when(ticketQueryService.getAccessibleTickets(eq(user.getId()), any(), anyList())).thenAnswer(invocation -> {
            List<UUID> requested = invocation.getArgument(2);
            return accessibleTickets.stream().filter((Ticket ticket) -> requested.contains(ticket.getId())).toList();
        });
    }

    private DailyUpdate storedUpdate(User author, String focus, String questions, DailyUpdateTicket... items) {
        DailyUpdate dailyUpdate = DailyUpdateMocks.dailyUpdate(author, focus, questions, items);
        dailyUpdate.setOrganizationId(organizationId);
        return dailyUpdate;
    }

    private void givenContributorOn(Board... boards) {
        // Lenient: the lookup is skipped when no line is a write-back candidate.
        lenient().when(boardMemberQueryService.getContributorBoardIds(eq(user.getId()), any()))
                .thenReturn(Arrays.stream(boards).map(Board::getId).toList());
    }

    private void givenVisible(DailyUpdate dailyUpdate) {
        when(organizationMemberQueryService.getColleagueUserIds(user.getId())).thenReturn(Set.of(user.getId()));
        when(dailyUpdateRepository.findOne(any(Specification.class))).thenReturn(Optional.of(dailyUpdate));
    }

    private void givenSaveReturnsArgument() {
        when(dailyUpdateRepository.save(any(DailyUpdate.class))).thenAnswer(invocation -> {
            DailyUpdate dailyUpdate = invocation.getArgument(0);
            if (dailyUpdate.getId() == null) {
                dailyUpdate.setId(UUID.randomUUID());
            }
            return dailyUpdate;
        });
    }

    private static DailyUpdateCreateRequest create(String focus, String questions, DailyUpdateTicketRequest... lines) {
        return new DailyUpdateCreateRequest(List.of(lines), focus, questions);
    }

    private static DailyUpdateUpdateRequest edit(String focus, String questions, DailyUpdateTicketRequest... lines) {
        return new DailyUpdateUpdateRequest(List.of(lines), focus, questions);
    }

    @Nested
    class CreateDailyUpdate {

        @Test
        void validRequest_savesUpdateWithSnapshotsInOrderAndNullForBlankText() {
            Ticket first = DailyUpdateMocks.ticket(board, "ACME-1", "First", 0, user);
            Ticket second = DailyUpdateMocks.ticket(board, "ACME-2", "Second", 50);
            givenAccessible(first, second);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            DailyUpdateResponse response = service.createDailyUpdate(user, null, create("  Ship it \n", "Who reviews?",
                    DailyUpdateMocks.line(second, 50, " "),
                    DailyUpdateMocks.line(first, 20, "Waiting")));

            ArgumentCaptor<DailyUpdate> saved = ArgumentCaptor.forClass(DailyUpdate.class);
            verify(dailyUpdateRepository).save(saved.capture());
            DailyUpdate dailyUpdate = saved.getValue();
            assertThat(dailyUpdate.getAuthor()).isSameAs(user);
            assertThat(dailyUpdate.getFocus()).isEqualTo("Ship it");
            assertThat(dailyUpdate.getQuestions()).isEqualTo("Who reviews?");
            assertThat(dailyUpdate.getEditedAt()).isNull();
            assertThat(dailyUpdate.getItems()).extracting(DailyUpdateTicket::getTicketKey)
                    .containsExactly("ACME-2", "ACME-1");
            assertThat(dailyUpdate.getItems()).extracting(DailyUpdateTicket::getPosition).containsExactly(0, 1);
            assertThat(dailyUpdate.getItems()).extracting(DailyUpdateTicket::getNote).containsExactly(null, "Waiting");
            assertThat(dailyUpdate.getItems().get(1).getBoardName()).isEqualTo("Fabrikam Board");
            assertThat(dailyUpdate.getItems().get(1).getBoardId()).isEqualTo(board.getId());
            assertThat(dailyUpdate.getItems().get(1).getTicketTitle()).isEqualTo("First");
            assertThat(response.items()).hasSize(2);
            assertThat(response.items()).allSatisfy(item -> assertThat(item.canOpen()).isTrue());
            assertThat(response.summaryText()).contains("   - ACME-2: Second", "   - ACME-1: 20%");
        }

        @Test
        void questionsWithSurroundingSpaces_areStoredAsWritten() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "First", 0, user);
            givenAccessible(ticket);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            service.createDailyUpdate(user, null, create("focus", "  Who reviews? ",
                    DailyUpdateMocks.line(ticket, 20, null)));

            ArgumentCaptor<DailyUpdate> saved = ArgumentCaptor.forClass(DailyUpdate.class);
            verify(dailyUpdateRepository).save(saved.capture());
            assertThat(saved.getValue().getQuestions()).isEqualTo("  Who reviews? ");
        }

        @Test
        void organizationHeader_isStoredAndTheUpdateIsPostedToItsChannel() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "First", 0, user);
            givenAccessible(ticket);
            givenContributorOn(board);
            givenSaveReturnsArgument();
            UUID organizationId = UUID.randomUUID();
            UUID channelId = UUID.randomUUID();
            when(chatChannelQueryService.findDailyUpdateChannelId(organizationId)).thenReturn(Optional.of(channelId));

            service.createDailyUpdate(user, organizationId, create(null, null, DailyUpdateMocks.line(ticket, 0, null)));

            ArgumentCaptor<DailyUpdate> saved = ArgumentCaptor.forClass(DailyUpdate.class);
            verify(dailyUpdateRepository).save(saved.capture());
            assertThat(saved.getValue().getOrganizationId()).isEqualTo(organizationId);
            ArgumentCaptor<ChatMessageCreateRequest> entry = ArgumentCaptor.forClass(ChatMessageCreateRequest.class);
            verify(chatMessageService).postMessage(eq(user), eq(organizationId), entry.capture(), eq(List.of()));
            UUID dailyUpdateId = saved.getValue().getId();
            assertThat(entry.getValue()).isEqualTo(new ChatMessageCreateRequest(null, channelId, null, null,
                    dailyUpdateId, null, dailyUpdateId.toString()));
            verify(organizationMemberQueryService, never()).findPrimaryActiveOrganizationId(any());
            verify(organizationAccessService).verifyOrganizationAccess(organizationId, user.getId(),
                    OrganizationMemberRole.MEMBER, App.KAIZEN);
        }

        @Test
        void disabledOrganizationHeader_throwsForbiddenAndSavesNothing() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "First", 0, user);
            givenAccessible(ticket);
            givenContributorOn(board);
            UUID organizationId = UUID.randomUUID();
            doThrow(new ForbiddenException(ExceptionDomainOrganizationError.SEC_ORGANIZATION_DISABLED))
                    .when(organizationAccessService)
                    .verifyOrganizationAccess(organizationId, user.getId(), OrganizationMemberRole.MEMBER,
                            App.KAIZEN);

            assertThatThrownBy(() -> service.createDailyUpdate(user, organizationId,
                    create(null, null, DailyUpdateMocks.line(ticket, 0, null))))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode",
                            ExceptionDomainOrganizationError.SEC_ORGANIZATION_DISABLED.getCode());

            verify(dailyUpdateRepository, never()).save(any(DailyUpdate.class));
            verifyNoInteractions(chatMessageService, ticketService);
        }

        @Test
        void guestOfTheOrganization_throwsForbiddenAndSavesNothing() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "First", 0, user);
            givenAccessible(ticket);
            givenContributorOn(board);
            UUID organizationId = UUID.randomUUID();
            doThrow(new ForbiddenException(ExceptionDomainOrganizationError.SEC_INSUFFICIENT_PERMISSIONS))
                    .when(organizationAccessService)
                    .verifyOrganizationAccess(organizationId, user.getId(), OrganizationMemberRole.MEMBER,
                            App.KAIZEN);

            assertThatThrownBy(() -> service.createDailyUpdate(user, organizationId,
                    create(null, null, DailyUpdateMocks.line(ticket, 0, null))))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode",
                            ExceptionDomainOrganizationError.SEC_INSUFFICIENT_PERMISSIONS.getCode());

            verify(dailyUpdateRepository, never()).save(any(DailyUpdate.class));
            verifyNoInteractions(chatMessageService, chatChannelQueryService, ticketService);
        }

        @Test
        void organizationWithoutDailyUpdatesChannel_savesTheUpdateWithoutAnEntry() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "First", 0, user);
            givenAccessible(ticket);
            givenContributorOn(board);
            givenSaveReturnsArgument();
            UUID organizationId = UUID.randomUUID();
            when(chatChannelQueryService.findDailyUpdateChannelId(organizationId)).thenReturn(Optional.empty());

            service.createDailyUpdate(user, organizationId, create(null, null, DailyUpdateMocks.line(ticket, 0, null)));

            verify(dailyUpdateRepository).save(any(DailyUpdate.class));
            verifyNoInteractions(chatMessageService);
        }

        @Test
        void noHeader_usesTheAuthorsPrimaryActiveOrganization() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "First", 0, user);
            givenAccessible(ticket);
            givenContributorOn(board);
            givenSaveReturnsArgument();
            UUID primaryOrganizationId = UUID.randomUUID();
            when(organizationMemberQueryService.findPrimaryActiveOrganizationId(user.getId()))
                    .thenReturn(Optional.of(primaryOrganizationId));
            UUID channelId = UUID.randomUUID();
            when(chatChannelQueryService.findDailyUpdateChannelId(primaryOrganizationId))
                    .thenReturn(Optional.of(channelId));

            service.createDailyUpdate(user, null, create(null, null, DailyUpdateMocks.line(ticket, 0, null)));

            ArgumentCaptor<DailyUpdate> saved = ArgumentCaptor.forClass(DailyUpdate.class);
            verify(dailyUpdateRepository).save(saved.capture());
            assertThat(saved.getValue().getOrganizationId()).isEqualTo(primaryOrganizationId);
            verify(chatMessageService).postMessage(eq(user), eq(primaryOrganizationId),
                    any(ChatMessageCreateRequest.class), eq(List.of()));
            verify(organizationAccessService).verifyOrganizationAccess(primaryOrganizationId, user.getId(),
                    OrganizationMemberRole.MEMBER, App.KAIZEN);
        }

        @Test
        void noHeaderAndNoActiveOrganization_throwsNotFoundAndSavesNothing() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "First", 0, user);
            givenAccessible(ticket);
            when(organizationMemberQueryService.findPrimaryActiveOrganizationId(user.getId()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createDailyUpdate(user, null,
                    create(null, null, DailyUpdateMocks.line(ticket, 0, null))))
                    .isInstanceOf(NotFoundException.class);

            verify(dailyUpdateRepository, never()).save(any(DailyUpdate.class));
            verifyNoInteractions(chatMessageService, chatChannelQueryService, organizationAccessService);
        }

        @Test
        void zeroTickets_throwsValidationExceptionAndSavesNothing() {
            DailyUpdateCreateRequest request = new DailyUpdateCreateRequest(List.of(), null, null);

            assertThatThrownBy(() -> service.createDailyUpdate(user, null, request)).isInstanceOf(ValidationException.class);

            verify(dailyUpdateRepository, never()).save(any());
            verifyNoInteractions(ticketQueryService, ticketService);
        }

        @Test
        void thirtyOneTickets_throwsValidationExceptionAndSavesNothing() {
            List<DailyUpdateTicketRequest> lines = new ArrayList<>();
            for (int index = 0; index < 31; index++) {
                lines.add(new DailyUpdateTicketRequest(UUID.randomUUID(), 10, null));
            }

            assertThatThrownBy(() -> service.createDailyUpdate(user, null, new DailyUpdateCreateRequest(lines, null, null)))
                    .isInstanceOf(ValidationException.class);

            verify(dailyUpdateRepository, never()).save(any());
        }

        @Test
        void duplicateTicket_throwsValidationExceptionAndSavesNothing() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "T", 0, user);

            assertThatThrownBy(() -> service.createDailyUpdate(user, null, create(null, null,
                    DailyUpdateMocks.line(ticket, 10, null), DailyUpdateMocks.line(ticket, 20, null))))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainKaizenError.VAL_DAILY_UPDATE_DUPLICATE_TICKET.getCode()));

            verify(dailyUpdateRepository, never()).save(any());
        }

        @Test
        void noteOf501Characters_throwsValidationExceptionAndSavesNothing() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "T", 0, user);

            assertThatThrownBy(() -> service.createDailyUpdate(user, null, create(null, null,
                    DailyUpdateMocks.line(ticket, 10, "n".repeat(501)))))
                    .isInstanceOf(ValidationException.class);

            verify(dailyUpdateRepository, never()).save(any());
        }

        @Test
        void noteOf500Characters_isStored() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "T", 0);
            givenAccessible(ticket);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            DailyUpdateResponse response = service.createDailyUpdate(user, null, create(null, null,
                    DailyUpdateMocks.line(ticket, 10, "n".repeat(500))));

            assertThat(response.items().getFirst().note()).hasSize(500);
        }

        @Test
        void inaccessibleTicket_throwsNotFoundSavesNothingAndWritesNoProgress() {
            Ticket reachable = DailyUpdateMocks.ticket(board, "ACME-1", "T", 0, user);
            Ticket foreign = DailyUpdateMocks.ticket(DailyUpdateMocks.board("Other"), "OTH-1", "X", 0);
            givenAccessible(reachable);

            assertThatThrownBy(() -> service.createDailyUpdate(user, null, create(null, null,
                    DailyUpdateMocks.line(reachable, 40, null), DailyUpdateMocks.line(foreign, 40, null))))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainKaizenError.NOT_TICKET_FOUND.getCode()));

            verify(dailyUpdateRepository, never()).save(any());
            verifyNoInteractions(ticketService);
            verifyNoInteractions(chatMessageService);
        }

        @Test
        void assigneeWithContributorRoleAndChangedValue_syncsProgressAndMarksLine() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "T", 10, user);
            givenAccessible(ticket);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            DailyUpdateResponse response = service.createDailyUpdate(user, null, create(null, null,
                    DailyUpdateMocks.line(ticket, 60, null)));

            verify(ticketService).syncTicketPercentage(user, organizationId, ticket.getId(), 60);
            assertThat(response.items().getFirst().progressSynced()).isTrue();
        }

        @Test
        void viewerOnBoard_doesNotSyncProgress() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "T", 10, user);
            givenAccessible(ticket);
            givenContributorOn();
            givenSaveReturnsArgument();

            DailyUpdateResponse response = service.createDailyUpdate(user, null, create(null, null,
                    DailyUpdateMocks.line(ticket, 60, null)));

            verify(ticketService, never()).syncTicketPercentage(any(), eq(organizationId), any(), anyInt());
            assertThat(response.items().getFirst().progressSynced()).isFalse();
            verify(dailyUpdateRepository).save(any(DailyUpdate.class));
        }

        @Test
        void nonAssigneeContributor_syncsProgressAndMarksLine() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "T", 10, DailyUpdateMocks.user());
            givenAccessible(ticket);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            DailyUpdateResponse response = service.createDailyUpdate(user, null, create(null, null,
                    DailyUpdateMocks.line(ticket, 60, null)));

            verify(ticketService).syncTicketPercentage(user, organizationId, ticket.getId(), 60);
            assertThat(response.items().getFirst().progressSynced()).isTrue();
        }

        @Test
        void unassignedTicketOnContributorBoard_syncsProgress() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "T", 10);
            givenAccessible(ticket);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            DailyUpdateResponse response = service.createDailyUpdate(user, null, create(null, null,
                    DailyUpdateMocks.line(ticket, 60, null)));

            verify(ticketService).syncTicketPercentage(user, organizationId, ticket.getId(), 60);
            assertThat(response.items().getFirst().progressSynced()).isTrue();
        }

        @Test
        void nonAssigneeOnViewerBoard_doesNotSyncProgress() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "T", 10, DailyUpdateMocks.user());
            givenAccessible(ticket);
            givenContributorOn();
            givenSaveReturnsArgument();

            DailyUpdateResponse response = service.createDailyUpdate(user, null, create(null, null,
                    DailyUpdateMocks.line(ticket, 60, null)));

            verify(ticketService, never()).syncTicketPercentage(any(), eq(organizationId), any(), anyInt());
            assertThat(response.items().getFirst().progressSynced()).isFalse();
        }

        @Test
        void unchangedValue_doesNotSyncProgress() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "T", 60, user);
            givenAccessible(ticket);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            DailyUpdateResponse response = service.createDailyUpdate(user, null, create(null, null,
                    DailyUpdateMocks.line(ticket, 60, null)));

            verify(ticketService, never()).syncTicketPercentage(any(), eq(organizationId), any(), anyInt());
            assertThat(response.items().getFirst().progressSynced()).isFalse();
        }

        @Test
        void rejectedWriteBack_propagatesSoTheTransactionRollsBack() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "T", 10, user);
            givenAccessible(ticket);
            givenContributorOn(board);
            givenSaveReturnsArgument();
            doThrow(new ForbiddenException()).when(ticketService).syncTicketPercentage(user, organizationId, ticket.getId(), 60);

            assertThatThrownBy(() -> service.createDailyUpdate(user, null, create(null, null,
                    DailyUpdateMocks.line(ticket, 60, null))))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class EditDailyUpdate {

        private Ticket ticketA;
        private Ticket ticketB;
        private DailyUpdateTicket itemA;
        private DailyUpdateTicket itemB;
        private DailyUpdate stored;

        @BeforeEach
        void setUpStoredUpdate() {
            ticketA = DailyUpdateMocks.ticket(board, "ACME-1", "A now", 10, user);
            ticketB = DailyUpdateMocks.ticket(board, "ACME-2", "B now", 20, user);
            itemA = DailyUpdateMocks.item(ticketA, 10, "a", 0);
            itemB = DailyUpdateMocks.item(ticketB, 20, null, 1);
            stored = storedUpdate(user, "old focus", "old questions", itemA, itemB);
        }

        @Test
        void authorChangesPercentageNoteAndOrder_updatesLinesInPlaceAndSetsEditedAt() {
            givenVisible(stored);
            givenAccessible(ticketA, ticketB);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            DailyUpdateResponse response = service.editDailyUpdate(user, stored.getId(), edit(" new focus ", " ", 
                    DailyUpdateMocks.line(ticketB, 20, "nb"),
                    DailyUpdateMocks.line(ticketA, 10, "a2")));

            assertThat(stored.getItems()).containsExactly(itemB, itemA);
            assertThat(itemB.getPosition()).isZero();
            assertThat(itemA.getPosition()).isEqualTo(1);
            assertThat(itemB.getNote()).isEqualTo("nb");
            assertThat(itemA.getNote()).isEqualTo("a2");
            assertThat(itemA.getTicketTitle()).as("snapshot kept, not refreshed").isEqualTo("A now");
            assertThat(stored.getFocus()).isEqualTo("new focus");
            assertThat(stored.getQuestions()).isNull();
            assertThat(stored.getEditedAt()).isNotNull();
            assertThat(response.editedAt()).isNotNull();
            verify(dailyUpdateRepository).save(stored);
            verify(chatMessageService).touchDailyUpdateMessage(stored);
        }

        @Test
        void keptLineWithNewPercentage_changesItInPlace() {
            givenVisible(stored);
            givenAccessible(ticketA, ticketB);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            service.editDailyUpdate(user, stored.getId(), edit(null, null,
                    DailyUpdateMocks.line(ticketA, 75, "a"), DailyUpdateMocks.line(ticketB, 20, null)));

            assertThat(stored.getItems()).containsExactly(itemA, itemB);
            assertThat(itemA.getPercentage()).isEqualTo(75);
        }

        @Test
        void addedTicket_getsSnapshotAndPosition() {
            Ticket ticketC = DailyUpdateMocks.ticket(board, "ACME-3", "C", 0);
            givenVisible(stored);
            givenAccessible(ticketA, ticketB, ticketC);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            service.editDailyUpdate(user, stored.getId(), edit(null, null,
                    DailyUpdateMocks.line(ticketA, 10, "a"),
                    DailyUpdateMocks.line(ticketB, 20, null),
                    DailyUpdateMocks.line(ticketC, 5, "new")));

            assertThat(stored.getItems()).hasSize(3);
            DailyUpdateTicket added = stored.getItems().get(2);
            assertThat(added.getTicketId()).isEqualTo(ticketC.getId());
            assertThat(added.getTicketKey()).isEqualTo("ACME-3");
            assertThat(added.getBoardName()).isEqualTo("Fabrikam Board");
            assertThat(added.getPosition()).isEqualTo(2);
            assertThat(added.getDailyUpdate()).isSameAs(stored);
        }

        @Test
        void addedTicketOnNonMemberBoard_throwsNotFoundAndSavesNothing() {
            Ticket foreign = DailyUpdateMocks.ticket(DailyUpdateMocks.board("Other"), "OTH-1", "X", 0);
            givenVisible(stored);
            givenAccessible(ticketA, ticketB);

            assertThatThrownBy(() -> service.editDailyUpdate(user, stored.getId(), edit(null, null,
                    DailyUpdateMocks.line(ticketA, 10, "a"), DailyUpdateMocks.line(foreign, 5, null))))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainKaizenError.NOT_TICKET_FOUND.getCode()));

            verify(dailyUpdateRepository, never()).save(any());
            assertThat(stored.getItems()).containsExactly(itemA, itemB);
            assertThat(stored.getEditedAt()).isNull();
        }

        @Test
        void omittedLine_isRemoved() {
            givenVisible(stored);
            givenAccessible(ticketA, ticketB);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            service.editDailyUpdate(user, stored.getId(), edit(null, null, DailyUpdateMocks.line(ticketB, 20, null)));

            assertThat(stored.getItems()).containsExactly(itemB);
            assertThat(itemB.getPosition()).isZero();
        }

        @Test
        void keptLineOnBoardTheAuthorLeft_survivesWithSnapshotAndWritesNothing() {
            // Ticket A is no longer reachable: the author left its board.
            givenVisible(stored);
            givenAccessible(ticketB);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            DailyUpdateResponse response = service.editDailyUpdate(user, stored.getId(), edit(null, null,
                    DailyUpdateMocks.line(ticketA, 90, "still mine"), DailyUpdateMocks.line(ticketB, 20, null)));

            assertThat(stored.getItems()).containsExactly(itemA, itemB);
            assertThat(itemA.getPercentage()).isEqualTo(90);
            assertThat(itemA.getTicketKey()).isEqualTo("ACME-1");
            verify(ticketService, never()).syncTicketPercentage(any(), eq(organizationId), any(), anyInt());
            assertThat(response.items().getFirst().canOpen()).isFalse();
            assertThat(response.items().get(1).canOpen()).isTrue();
        }

        @Test
        void lineWithoutTicketKept_staysUnchangedAtItsIndex() {
            DailyUpdateTicket gone = DailyUpdateMocks.ticketlessItem("DEL-1", "Deleted ticket", 70, 1);
            stored = storedUpdate(user, null, null, itemA, gone, itemB);
            givenVisible(stored);
            givenAccessible(ticketA, ticketB);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            service.editDailyUpdate(user, stored.getId(), edit(null, null,
                    DailyUpdateMocks.line(ticketB, 20, null), DailyUpdateMocks.line(ticketA, 10, "a")));

            assertThat(stored.getItems()).containsExactly(itemB, gone, itemA);
            assertThat(gone.getPercentage()).isEqualTo(70);
            assertThat(gone.getTicketKey()).isEqualTo("DEL-1");
            assertThat(stored.getItems()).extracting(DailyUpdateTicket::getPosition).containsExactly(0, 1, 2);
        }

        @Test
        void writeBack_onlyForAddedAndChangedLinesUnderTheCreateRule() {
            Ticket ticketC = DailyUpdateMocks.ticket(board, "ACME-3", "C", 0, user);
            Ticket ticketD = DailyUpdateMocks.ticket(board, "ACME-4", "D", 0, DailyUpdateMocks.user());
            Board viewerBoard = DailyUpdateMocks.board("Viewer board");
            Ticket ticketE = DailyUpdateMocks.ticket(viewerBoard, "VWR-1", "E", 0, user);
            givenVisible(stored);
            givenAccessible(ticketA, ticketB, ticketC, ticketD, ticketE);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            DailyUpdateResponse response = service.editDailyUpdate(user, stored.getId(), edit(null, null,
                    DailyUpdateMocks.line(ticketA, 10, "a"),
                    DailyUpdateMocks.line(ticketB, 60, null),
                    DailyUpdateMocks.line(ticketC, 40, null),
                    DailyUpdateMocks.line(ticketD, 40, null),
                    DailyUpdateMocks.line(ticketE, 40, null)));

            verify(ticketService).syncTicketPercentage(user, organizationId, ticketB.getId(), 60);
            verify(ticketService).syncTicketPercentage(user, organizationId, ticketC.getId(), 40);
            verify(ticketService).syncTicketPercentage(user, organizationId, ticketD.getId(), 40);
            verify(ticketService, never()).syncTicketPercentage(eq(user), eq(organizationId), eq(ticketA.getId()), anyInt());
            verify(ticketService, never()).syncTicketPercentage(eq(user), eq(organizationId), eq(ticketE.getId()), anyInt());
            assertThat(response.items()).extracting(item -> item.progressSynced())
                    .containsExactly(false, true, true, true, false);
        }

        @Test
        void changedLineWhoseTicketAlreadyHoldsTheValue_doesNotSync() {
            ticketB.setPercentage(60);
            givenVisible(stored);
            givenAccessible(ticketA, ticketB);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            service.editDailyUpdate(user, stored.getId(), edit(null, null,
                    DailyUpdateMocks.line(ticketA, 10, "a"), DailyUpdateMocks.line(ticketB, 60, null)));

            verify(ticketService, never()).syncTicketPercentage(any(), eq(organizationId), any(), anyInt());
        }

        @Test
        void previouslySyncedLineEditedWithoutSync_staysMarkedSynced() {
            itemA.setProgressSynced(true);
            givenVisible(stored);
            givenAccessible(ticketA, ticketB);
            givenContributorOn(board);
            givenSaveReturnsArgument();

            DailyUpdateResponse response = service.editDailyUpdate(user, stored.getId(), edit(null, null,
                    DailyUpdateMocks.line(ticketA, 10, "other note"), DailyUpdateMocks.line(ticketB, 20, null)));

            assertThat(itemA.isProgressSynced()).isTrue();
            assertThat(response.items().getFirst().progressSynced()).isTrue();
        }

        @Test
        void nonAuthor_throwsForbiddenAndChangesNothing() {
            User other = DailyUpdateMocks.user();
            when(organizationMemberQueryService.getColleagueUserIds(other.getId()))
                    .thenReturn(Set.of(other.getId(), user.getId()));
            when(dailyUpdateRepository.findOne(any(Specification.class))).thenReturn(Optional.of(stored));

            assertThatThrownBy(() -> service.editDailyUpdate(other, stored.getId(), edit(null, null,
                    DailyUpdateMocks.line(ticketA, 99, "x"))))
                    .isInstanceOfSatisfying(ForbiddenException.class, (ForbiddenException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainKaizenError.SEC_CANNOT_MODIFY_OTHERS_UPDATE.getCode()));

            verify(dailyUpdateRepository, never()).save(any());
            assertThat(itemA.getPercentage()).isEqualTo(10);
            verifyNoInteractions(ticketService);
            verifyNoInteractions(chatMessageService);
        }

        @Test
        void invisibleUpdate_throwsNotFound() {
            when(organizationMemberQueryService.getColleagueUserIds(user.getId())).thenReturn(Set.of(user.getId()));
            when(dailyUpdateRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.editDailyUpdate(user, UUID.randomUUID(), edit(null, null,
                    DailyUpdateMocks.line(ticketA, 10, null))))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainKaizenError.NOT_DAILY_UPDATE_FOUND.getCode()));

            verify(dailyUpdateRepository, never()).save(any());
        }

        @Test
        void editRequestBreakingLimits_throwsValidationException() {
            givenVisible(stored);

            assertThatThrownBy(() -> service.editDailyUpdate(user, stored.getId(), edit(null, null,
                    DailyUpdateMocks.line(ticketA, 10, "n".repeat(501)))))
                    .isInstanceOf(ValidationException.class);

            verify(dailyUpdateRepository, never()).save(any());
        }
    }

    @Nested
    class DeleteDailyUpdate {

        @Test
        void author_deletesUpdateWithoutTouchingItsLines() {
            Ticket ticket = DailyUpdateMocks.ticket(board, "ACME-1", "T", 10, user);
            DailyUpdateTicket item = DailyUpdateMocks.item(ticket, 10, null, 0);
            DailyUpdate stored = storedUpdate(user, null, null, item);
            givenVisible(stored);

            service.deleteDailyUpdate(user, stored.getId());

            verify(dailyUpdateRepository).deleteByIdWithoutRelations(stored.getId());
            verify(dailyUpdateRepository, never()).delete(any(DailyUpdate.class));
            assertThat(stored.getItems()).containsExactly(item);
            verifyNoInteractions(ticketService);
            verify(chatMessageService).removeDailyUpdateMessage(stored);
        }

        @Test
        void nonAuthor_throwsForbidden() {
            DailyUpdate stored = storedUpdate(DailyUpdateMocks.user(), null, null);
            givenVisible(stored);

            assertThatThrownBy(() -> service.deleteDailyUpdate(user, stored.getId()))
                    .isInstanceOf(ForbiddenException.class);

            verify(dailyUpdateRepository, never()).deleteByIdWithoutRelations(any());
            verifyNoInteractions(chatMessageService);
        }

        @Test
        void invisibleUpdate_throwsNotFound() {
            when(organizationMemberQueryService.getColleagueUserIds(user.getId())).thenReturn(Set.of(user.getId()));
            when(dailyUpdateRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteDailyUpdate(user, UUID.randomUUID()))
                    .isInstanceOf(NotFoundException.class);

            verify(dailyUpdateRepository, never()).deleteByIdWithoutRelations(any());
        }
    }
}
