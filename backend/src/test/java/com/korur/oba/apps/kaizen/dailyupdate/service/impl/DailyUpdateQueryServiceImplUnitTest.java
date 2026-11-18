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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

// 3. Java SDK
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdatePreviewRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdateQueryRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdateTicketRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdatePreviewResponse;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateResponse;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateTicketResponse;
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
import com.korur.oba.apps.kaizen.ticket.dto.response.AllTicketListResponse;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.mapper.TicketEmbeddedMapperImpl;
import com.korur.oba.apps.kaizen.ticket.service.TicketQueryService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.time.BusinessCalendar;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.service.OrganizationAccessService;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class DailyUpdateQueryServiceImplUnitTest {

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

    private DailyUpdateQueryServiceImpl service;
    private User user;
    private User colleague;
    private Board memberBoard;
    private Board otherBoard;
    private final List<Ticket> accessibleTickets = new ArrayList<>();

    @BeforeEach
    void setUp() {
        DailyUpdateValidator validator = new DailyUpdateValidator(dailyUpdateRepository, new LoggingService());
        DailyUpdateMapperImpl mapper = new DailyUpdateMapperImpl(new TicketEmbeddedMapperImpl());
        DailyUpdateMapperHelper mapperHelper = new DailyUpdateMapperHelper(ticketQueryService,
                mapper, new DailyUpdateSummaryRenderer(), validator, organizationMemberQueryService);
        Clock clock = Clock.systemUTC();
        BusinessCalendar businessCalendar = new BusinessCalendar(clock);
        service = new DailyUpdateQueryServiceImpl(dailyUpdateRepository, validator, mapper, mapperHelper,
                new DailyUpdateDeadlineResolver(validator, businessCalendar, clock), ticketQueryService,
                boardMemberQueryService, organizationMemberQueryService, organizationAccessService,
                appAccessQueryService, businessCalendar);

        user = DailyUpdateMocks.user();
        colleague = DailyUpdateMocks.user();
        memberBoard = DailyUpdateMocks.board("Member board");
        otherBoard = DailyUpdateMocks.board("Other board");
        lenient().when(organizationMemberQueryService.findMember(organizationId, user.getId()))
                .thenReturn(Optional.of(DailyUpdateMocks.membership(OrganizationStatus.ACTIVE,
                        OrganizationMemberStatus.ACTIVE)));
        lenient().when(organizationMemberQueryService.getColleagueUserIds(user.getId()))
                .thenReturn(Set.of(user.getId(), colleague.getId()));
    }

    private DailyUpdate dailyUpdate(User author, String focus, String questions, DailyUpdateTicket... items) {
        DailyUpdate dailyUpdate = DailyUpdateMocks.dailyUpdate(author, focus, questions, items);
        dailyUpdate.setOrganizationId(organizationId);
        return dailyUpdate;
    }

    private void givenAccessible(Ticket... tickets) {
        accessibleTickets.clear();
        accessibleTickets.addAll(List.of(tickets));
        when(ticketQueryService.getAccessibleTickets(eq(user.getId()), eq(organizationId), anyList())).thenAnswer(invocation -> {
            List<UUID> requested = invocation.getArgument(2);
            return accessibleTickets.stream().filter((Ticket ticket) -> requested.contains(ticket.getId())).toList();
        });
    }

    private void givenContributorOn(Board... boards) {
        lenient().when(boardMemberQueryService.getContributorBoardIds(user.getId(), organizationId))
                .thenReturn(Arrays.stream(boards).map(Board::getId).toList());
    }

    private void givenVisible(DailyUpdate dailyUpdate) {
        when(dailyUpdateRepository.findOne(any(Specification.class))).thenReturn(Optional.of(dailyUpdate));
    }

    @Nested
    class PreviewDailyUpdate {

        @Test
        void validRequest_returnsLinesAndTextAndSavesNothing() {
            Ticket ticket = DailyUpdateMocks.ticket(memberBoard, "ACME-1", "Menu", 10, user);
            givenAccessible(ticket);
            givenContributorOn(memberBoard);

            DailyUpdatePreviewResponse preview = service.previewDailyUpdate(user, organizationId, new DailyUpdatePreviewRequest(
                    List.of(DailyUpdateMocks.line(ticket, 60, "note")), "focus", null));

            assertThat(preview.items()).hasSize(1);
            assertThat(preview.items().getFirst().progressSynced()).as("would sync").isTrue();
            assertThat(preview.items().getFirst().canOpen()).isTrue();
            assertThat(preview.summaryText()).contains("   - ACME-1: Menu", "     - note", "   - ACME-1: 60%");
            verify(dailyUpdateRepository, never()).save(any());
            verify(dailyUpdateRepository, never()).saveAndFlush(any());
        }

        @Test
        void blankFocus_returnsNullFocus() {
            Ticket ticket = DailyUpdateMocks.ticket(memberBoard, "ACME-1", "Menu", 10, user);
            givenAccessible(ticket);
            givenContributorOn(memberBoard);

            DailyUpdatePreviewResponse preview = service.previewDailyUpdate(user, organizationId, new DailyUpdatePreviewRequest(
                    List.of(DailyUpdateMocks.line(ticket, 60, null)), "  ", null));

            assertThat(preview.focus()).isNull();
        }

        @Test
        void focusWithSurroundingSpaces_returnsStrippedFocus() {
            Ticket ticket = DailyUpdateMocks.ticket(memberBoard, "ACME-1", "Menu", 10, user);
            givenAccessible(ticket);
            givenContributorOn(memberBoard);

            DailyUpdatePreviewResponse preview = service.previewDailyUpdate(user, organizationId, new DailyUpdatePreviewRequest(
                    List.of(DailyUpdateMocks.line(ticket, 60, null)), "  Menu \n", null));

            assertThat(preview.focus()).isEqualTo("Menu");
        }

        @Test
        void unchangedLine_reportsNoSync() {
            Ticket unchanged = DailyUpdateMocks.ticket(memberBoard, "ACME-1", "A", 60, user);
            givenAccessible(unchanged);
            givenContributorOn(memberBoard);

            DailyUpdatePreviewResponse preview = service.previewDailyUpdate(user, organizationId, new DailyUpdatePreviewRequest(
                    List.of(DailyUpdateMocks.line(unchanged, 60, null)), null, null));

            assertThat(preview.items()).extracting(DailyUpdateTicketResponse::progressSynced)
                    .containsExactly(false);
        }

        @Test
        void changedLineAssignedToColleague_reportsSync() {
            Ticket foreign = DailyUpdateMocks.ticket(memberBoard, "ACME-2", "B", 0, colleague);
            givenAccessible(foreign);
            givenContributorOn(memberBoard);

            DailyUpdatePreviewResponse preview = service.previewDailyUpdate(user, organizationId, new DailyUpdatePreviewRequest(
                    List.of(DailyUpdateMocks.line(foreign, 30, null)), null, null));

            assertThat(preview.items()).extracting(DailyUpdateTicketResponse::progressSynced)
                    .containsExactly(true);
        }

        @Test
        void changedLineOnViewerBoard_reportsNoSync() {
            Ticket onViewerBoard = DailyUpdateMocks.ticket(otherBoard, "OTH-1", "X", 0, user);
            givenAccessible(onViewerBoard);
            givenContributorOn(memberBoard);

            DailyUpdatePreviewResponse preview = service.previewDailyUpdate(user, organizationId, new DailyUpdatePreviewRequest(
                    List.of(DailyUpdateMocks.line(onViewerBoard, 30, null)), null, null));

            assertThat(preview.items()).extracting(DailyUpdateTicketResponse::progressSynced)
                    .containsExactly(false);
        }

        @Test
        void inaccessibleTicket_throwsNotFound() {
            Ticket foreign = DailyUpdateMocks.ticket(otherBoard, "OTH-1", "X", 0);
            givenAccessible();

            assertThatThrownBy(() -> service.previewDailyUpdate(user, organizationId, new DailyUpdatePreviewRequest(
                    List.of(DailyUpdateMocks.line(foreign, 10, null)), null, null)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void duplicateTicketsOrTooManyOrLongNote_throwValidationException() {
            Ticket ticket = DailyUpdateMocks.ticket(memberBoard, "ACME-1", "A", 0, user);
            DailyUpdateTicketRequest line = DailyUpdateMocks.line(ticket, 10, null);

            assertThatThrownBy(() -> service.previewDailyUpdate(user,
                    organizationId,
                    new DailyUpdatePreviewRequest(List.of(line, line), null, null)))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> service.previewDailyUpdate(user,
                    organizationId,
                    new DailyUpdatePreviewRequest(List.of(), null, null)))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> service.previewDailyUpdate(user, organizationId, new DailyUpdatePreviewRequest(
                    List.of(DailyUpdateMocks.line(ticket, 10, "n".repeat(501))), null, null)))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class PreviewDailyUpdateEdit {

        private Ticket ticketA;
        private Ticket ticketB;
        private DailyUpdateTicket itemA;
        private DailyUpdateTicket itemB;
        private DailyUpdate stored;

        @BeforeEach
        void setUpStoredUpdate() {
            ticketA = DailyUpdateMocks.ticket(memberBoard, "ACME-1", "A", 10, user);
            ticketB = DailyUpdateMocks.ticket(memberBoard, "ACME-2", "B", 20, user);
            itemA = DailyUpdateMocks.item(ticketA, 10, "a", 0);
            itemB = DailyUpdateMocks.item(ticketB, 20, null, 1);
            stored = dailyUpdate(user, "f", "q", itemA, itemB);
        }

        @Test
        void authorEdit_returnsResultingLinesAndLeavesStoredUpdateUntouched() {
            Ticket ticketC = DailyUpdateMocks.ticket(memberBoard, "ACME-3", "C", 0, user);
            givenVisible(stored);
            givenAccessible(ticketA, ticketB, ticketC);
            givenContributorOn(memberBoard);

            DailyUpdatePreviewResponse preview = service.previewDailyUpdateEdit(user, stored.getId(),
                    new DailyUpdatePreviewRequest(List.of(
                            DailyUpdateMocks.line(ticketB, 80, "nb"),
                            DailyUpdateMocks.line(ticketA, 10, "a"),
                            DailyUpdateMocks.line(ticketC, 5, null)), "new focus", null));

            assertThat(preview.items()).extracting(DailyUpdateTicketResponse::ticketKey)
                    .containsExactly("ACME-2", "ACME-1", "ACME-3");
            assertThat(preview.items()).extracting(DailyUpdateTicketResponse::progressSynced)
                    .containsExactly(true, false, true);
            assertThat(preview.summaryText()).contains("   - new focus");
            assertThat(stored.getItems()).containsExactly(itemA, itemB);
            assertThat(itemB.getPercentage()).isEqualTo(20);
            assertThat(itemB.getPosition()).isEqualTo(1);
            assertThat(stored.getFocus()).isEqualTo("f");
            assertThat(stored.getEditedAt()).isNull();
            verify(dailyUpdateRepository, never()).save(any());
        }

        @Test
        void ticketlessLineAndRemovedLine_previewKeepsGoneLineAndDropsOmitted() {
            DailyUpdateTicket gone = DailyUpdateMocks.ticketlessItem("DEL-1", "Gone", 40, 2);
            stored = dailyUpdate(user, null, null, itemA, itemB, gone);
            givenVisible(stored);
            givenAccessible(ticketA, ticketB);
            givenContributorOn(memberBoard);

            DailyUpdatePreviewResponse preview = service.previewDailyUpdateEdit(user, stored.getId(),
                    new DailyUpdatePreviewRequest(List.of(DailyUpdateMocks.line(ticketA, 10, "a")), null, null));

            assertThat(preview.items()).extracting(DailyUpdateTicketResponse::ticketKey)
                    .containsExactly("ACME-1", "DEL-1");
            assertThat(preview.items().get(1).canOpen()).isFalse();
            assertThat(preview.items().get(1).progressSynced()).isFalse();
            assertThat(gone.getPosition()).as("stored line not renumbered").isEqualTo(2);
        }

        @Test
        void addedTicketOnNonMemberBoard_throwsNotFound() {
            Ticket foreign = DailyUpdateMocks.ticket(otherBoard, "OTH-1", "X", 0);
            givenVisible(stored);
            givenAccessible(ticketA, ticketB);

            assertThatThrownBy(() -> service.previewDailyUpdateEdit(user, stored.getId(),
                    new DailyUpdatePreviewRequest(List.of(DailyUpdateMocks.line(foreign, 10, null)), null, null)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void nonAuthor_throwsForbidden() {
            DailyUpdate foreignUpdate = dailyUpdate(colleague, null, null,
                    DailyUpdateMocks.item(ticketA, 10, null, 0));
            givenVisible(foreignUpdate);

            assertThatThrownBy(() -> service.previewDailyUpdateEdit(user, foreignUpdate.getId(),
                    new DailyUpdatePreviewRequest(List.of(DailyUpdateMocks.line(ticketA, 10, null)), null, null)))
                    .isInstanceOfSatisfying(ForbiddenException.class, (ForbiddenException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainKaizenError.SEC_CANNOT_MODIFY_OTHERS_UPDATE.getCode()));
        }

        @Test
        void invisibleUpdate_throwsNotFound() {
            when(dailyUpdateRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.previewDailyUpdateEdit(user, UUID.randomUUID(),
                    new DailyUpdatePreviewRequest(List.of(DailyUpdateMocks.line(ticketA, 10, null)), null, null)))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class ListSuggestedTickets {

        @Test
        void delegatesWithTheMaximumOfThirty() {
            List<AllTicketListResponse> suggested = List.of(mock(AllTicketListResponse.class));
            when(ticketQueryService.listActiveAssignedTickets(user.getId(), organizationId, 30)).thenReturn(suggested);

            assertThat(service.listSuggestedTickets(user, organizationId)).isSameAs(suggested);
        }
    }

    @Nested
    class ListDailyUpdates {

        @Test
        void colleagueFeed_returnsEveryLineWithCanOpenOnlyForReachableTicketsAndOneTicketQuery() {
            Ticket onMemberBoard = DailyUpdateMocks.ticket(memberBoard, "ACME-1", "Mine", 10);
            Ticket onOtherBoard = DailyUpdateMocks.ticket(otherBoard, "OTH-1", "Theirs", 10);
            Ticket deleted = DailyUpdateMocks.ticket(memberBoard, "ACME-9", "Deleted", 10);
            DailyUpdate first = dailyUpdate(colleague, "f", null,
                    DailyUpdateMocks.item(onMemberBoard, 10, null, 0), DailyUpdateMocks.item(onOtherBoard, 20, null, 1));
            DailyUpdate second = dailyUpdate(colleague, null, null,
                    DailyUpdateMocks.item(deleted, 30, null, 0),
                    DailyUpdateMocks.ticketlessItem("GONE-1", "Hard deleted", 40, 1));
            givenAccessible(onMemberBoard);
            when(dailyUpdateRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 25), 2));

            Page<DailyUpdateResponse> page = service.listDailyUpdates(user, new DailyUpdateQueryRequest());

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent().get(0).items()).extracting(DailyUpdateTicketResponse::canOpen)
                    .containsExactly(true, false);
            assertThat(page.getContent().get(0).items()).extracting(DailyUpdateTicketResponse::ticketKey)
                    .containsExactly("ACME-1", "OTH-1");
            assertThat(page.getContent().get(1).items()).extracting(DailyUpdateTicketResponse::canOpen)
                    .containsExactly(false, false);
            assertThat(page.getContent().get(1).items().get(1).ticketId()).isNull();
            assertThat(page.getContent().get(0).summaryText()).contains("OTH-1: Theirs");
            verify(ticketQueryService, times(1)).getAccessibleTickets(eq(user.getId()), eq(organizationId), anyList());
            verify(organizationMemberQueryService, times(1)).getColleagueUserIds(user.getId());
        }

        @Test
        void request_isPagedNewestFirstIgnoringRequestedSort() {
            DailyUpdateQueryRequest request = new DailyUpdateQueryRequest();
            request.setPage(3);
            request.setAmount(10);
            request.setSortBy("author");
            request.setSortDirection("asc");
            when(dailyUpdateRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(Page.empty());

            Page<DailyUpdateResponse> page = service.listDailyUpdates(user, request);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(dailyUpdateRepository).findAll(any(Specification.class), pageable.capture());
            assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
            assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
            assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection())
                    .isEqualTo(Sort.Direction.DESC);
            assertThat(page.getContent()).isEmpty();
            verifyNoInteractions(ticketQueryService);
        }

        @Test
        void fromAfterTo_throwsValidationExceptionBeforeQuerying() {
            DailyUpdateQueryRequest request = new DailyUpdateQueryRequest();
            request.setFrom(LocalDate.of(2026, 10, 20));
            request.setTo(LocalDate.of(2026, 10, 19));

            assertThatThrownBy(() -> service.listDailyUpdates(user, request)).isInstanceOf(ValidationException.class);

            verifyNoInteractions(dailyUpdateRepository);
        }
    }

    @Nested
    class GetDailyUpdate {

        @Test
        void colleaguesUpdate_returnsAllLinesWithCanOpenPerReader() {
            Ticket reachable = DailyUpdateMocks.ticket(memberBoard, "ACME-1", "A", 10);
            Ticket foreign = DailyUpdateMocks.ticket(otherBoard, "OTH-1", "B", 10);
            DailyUpdate update = dailyUpdate(colleague, null, null,
                    DailyUpdateMocks.item(reachable, 10, "n", 0), DailyUpdateMocks.item(foreign, 20, null, 1));
            givenVisible(update);
            givenAccessible(reachable);

            DailyUpdateResponse response = service.getDailyUpdate(user, update.getId());

            assertThat(response.id()).isEqualTo(update.getId());
            assertThat(response.author().userId()).isEqualTo(colleague.getId());
            assertThat(response.items()).hasSize(2);
            assertThat(response.items()).extracting(DailyUpdateTicketResponse::canOpen).containsExactly(true, false);
            assertThat(response.items().get(1).boardName()).isEqualTo("Other board");
            assertThat(response.items().get(0).note()).isEqualTo("n");
        }

        @Test
        void invisibleUpdate_throwsNotFound() {
            when(dailyUpdateRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getDailyUpdate(user, UUID.randomUUID()))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class FindDailyUpdate {

        @Test
        void liveUpdate_isReturnedWithoutAReader() {
            DailyUpdate update = dailyUpdate(colleague, null, null);
            when(dailyUpdateRepository.findById(update.getId())).thenReturn(Optional.of(update));

            assertThat(service.findDailyUpdate(update.getId())).containsSame(update);
            verifyNoInteractions(organizationMemberQueryService);
        }

        @Test
        void absentOrDeletedUpdate_isEmpty() {
            UUID updateId = UUID.randomUUID();
            when(dailyUpdateRepository.findById(updateId)).thenReturn(Optional.empty());

            assertThat(service.findDailyUpdate(updateId)).isEmpty();
        }
    }

    @Nested
    class GetDailyUpdatesByIds {

        @Test
        void visibleUpdates_returnsThemInRequestOrderWithOneTicketQueryAndDropsInvisibleIds() {
            Ticket reachable = DailyUpdateMocks.ticket(memberBoard, "ACME-1", "A", 10);
            Ticket foreign = DailyUpdateMocks.ticket(otherBoard, "OTH-1", "B", 10);
            DailyUpdate first = dailyUpdate(colleague, null, null,
                    DailyUpdateMocks.item(foreign, 20, null, 0));
            DailyUpdate second = dailyUpdate(user, null, null,
                    DailyUpdateMocks.item(reachable, 10, null, 0));
            UUID invisibleId = UUID.randomUUID();
            givenAccessible(reachable);
            when(dailyUpdateRepository.findAll(any(Specification.class))).thenReturn(List.of(second, first));

            Map<UUID, DailyUpdateResponse> result = service.getDailyUpdatesByIds(user,
                    List.of(first.getId(), invisibleId, second.getId()));

            assertThat(result.keySet()).containsExactly(first.getId(), second.getId());
            assertThat(result.get(first.getId()).items()).extracting(DailyUpdateTicketResponse::canOpen)
                    .containsExactly(false);
            assertThat(result.get(second.getId()).items()).extracting(DailyUpdateTicketResponse::canOpen)
                    .containsExactly(true);
            verify(dailyUpdateRepository, times(1)).findAll(any(Specification.class));
            verify(ticketQueryService, times(1)).getAccessibleTickets(eq(user.getId()), eq(organizationId), anyList());
            verify(organizationMemberQueryService, times(1)).getColleagueUserIds(user.getId());
        }

        @Test
        void nullOrEmptyIds_returnsEmptyMapWithoutQuerying() {
            assertThat(service.getDailyUpdatesByIds(user, null)).isEmpty();
            assertThat(service.getDailyUpdatesByIds(user, List.of())).isEmpty();

            verifyNoInteractions(dailyUpdateRepository, ticketQueryService);
            verify(organizationMemberQueryService, never()).getColleagueUserIds(any());
        }
    }
}
