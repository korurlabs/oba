package com.korur.oba.apps.kaizen.milestone.service.impl;

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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kaizen.milestone.dto.request.MilestoneCreateRequest;
import com.korur.oba.apps.kaizen.milestone.dto.request.MilestoneTicketsRequest;
import com.korur.oba.apps.kaizen.milestone.dto.request.MilestoneUpdateRequest;
import com.korur.oba.apps.kaizen.milestone.dto.response.MilestoneDetailResponse;
import com.korur.oba.apps.kaizen.milestone.entity.Milestone;
import com.korur.oba.apps.kaizen.milestone.entity.MilestoneDependency;
import com.korur.oba.apps.kaizen.milestone.enums.MilestoneStatus;
import com.korur.oba.apps.kaizen.milestone.helper.MilestoneHelper;
import com.korur.oba.apps.kaizen.milestone.mapper.MilestoneMapperImpl;
import com.korur.oba.apps.kaizen.milestone.repository.MilestoneDependencyRepository;
import com.korur.oba.apps.kaizen.milestone.repository.MilestoneRepository;
import com.korur.oba.apps.kaizen.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.apps.kaizen.ticket.repository.TicketRepository;
import com.korur.oba.apps.kaizen.ticket.validator.TicketValidator;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class MilestoneServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private MilestoneRepository milestoneRepository;
    @Mock
    private MilestoneDependencyRepository milestoneDependencyRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private BoardAccessService boardAccessService;
    @Mock
    private BoardMemberQueryService boardMemberQueryService;
    @Mock
    private TicketValidator ticketValidator;
    @Mock
    private MilestoneHelper milestoneHelper;

    private MilestoneServiceImpl service;
    private User user;
    private Board milestoneBoard;
    private Milestone milestone;

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        MilestoneValidator milestoneValidator = new MilestoneValidator(
                milestoneRepository, milestoneDependencyRepository, loggingService);
        service = new MilestoneServiceImpl(milestoneRepository, milestoneDependencyRepository, ticketRepository,
                boardAccessService, boardMemberQueryService, milestoneValidator, ticketValidator,
                new MilestoneMapperImpl(), milestoneHelper, loggingService);

        user = User.builder().build();
        user.setId(UUID.randomUUID());
        milestoneBoard = board(null);
        milestone = Milestone.builder().name("M").board(milestoneBoard).status(MilestoneStatus.OPEN)
                .targetDate(LocalDate.of(2026, 12, 1)).build();
        milestone.setId(UUID.randomUUID());
    }

    private static Board board(Board parent) {
        Board board = Board.builder().name("B").prefix("B").color("#112233").parentBoard(parent).build();
        board.setId(UUID.randomUUID());
        return board;
    }

    private static Ticket ticket(Board board, Milestone attached) {
        Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE)
                .board(board).milestone(attached).build();
        ticket.setId(UUID.randomUUID());
        return ticket;
    }

    private void grantMilestoneAccess() {
        when(boardAccessService.verifyMilestoneAccess(
                organizationId,
                milestoneBoard.getId(), milestone.getId(), user.getId(), BoardMemberRole.ADMIN))
                .thenReturn(milestone);
    }

    private MilestoneTicketsRequest request(Ticket... tickets) {
        return new MilestoneTicketsRequest(java.util.Arrays.stream(tickets).map(Ticket::getId).toList());
    }

    @Nested
    class LinkTickets {

        @Test
        void relatedBoards_attachesEveryTicket() {
            grantMilestoneAccess();
            Ticket own = ticket(milestoneBoard, null);
            Ticket child = ticket(board(milestoneBoard), null);
            when(ticketRepository.findAllById(any())).thenReturn(List.of(own, child));
            MilestoneDetailResponse response = mock(MilestoneDetailResponse.class);
            when(milestoneHelper.toDetailResponse(any(Milestone.class), anyCollection())).thenReturn(response);

            MilestoneDetailResponse result = service.linkTickets(
                    user, organizationId, milestoneBoard.getId(), milestone.getId(), request(own, child));

            assertThat(result).isSameAs(response);
            assertThat(own.getMilestone()).isSameAs(milestone);
            assertThat(child.getMilestone()).isSameAs(milestone);
            verify(boardAccessService).verifyBoardAccess(organizationId, child.getBoard().getId(), user.getId(), BoardMemberRole.MEMBER);
            verify(ticketRepository).saveAll(List.of(own, child));
        }

        @Test
        void oneUnrelatedTicket_throwsAndAttachesNone() {
            grantMilestoneAccess();
            Ticket own = ticket(milestoneBoard, null);
            Ticket stranger = ticket(board(null), null);
            when(ticketRepository.findAllById(any())).thenReturn(List.of(own, stranger));

            assertThatThrownBy(() -> service.linkTickets(
                    user, organizationId, milestoneBoard.getId(), milestone.getId(), request(own, stranger)))
                    .isInstanceOf(ValidationException.class);

            assertThat(own.getMilestone()).isNull();
            assertThat(stranger.getMilestone()).isNull();
            verify(ticketRepository, never()).saveAll(anyList());
        }
    }

    @Nested
    class UnlinkTickets {

        @Test
        void detachesOnlyTicketsOfThisMilestone() {
            grantMilestoneAccess();
            Milestone other = Milestone.builder().name("O").board(milestoneBoard).build();
            other.setId(UUID.randomUUID());
            Ticket mine = ticket(milestoneBoard, milestone);
            Ticket theirs = ticket(milestoneBoard, other);
            Ticket none = ticket(milestoneBoard, null);
            when(ticketRepository.findAllById(any())).thenReturn(List.of(mine, theirs, none));

            service.unlinkTickets(user, organizationId, milestoneBoard.getId(), milestone.getId(), request(mine, theirs, none));

            assertThat(mine.getMilestone()).isNull();
            assertThat(theirs.getMilestone()).isSameAs(other);
            ArgumentCaptor<List<Ticket>> saved = ArgumentCaptor.forClass(List.class);
            verify(ticketRepository).saveAll(saved.capture());
            assertThat(saved.getValue()).containsExactly(mine);
        }
    }

    @Nested
    class CloseAndReopen {

        @Test
        void close_openMilestone_setsClosed() {
            grantMilestoneAccess();
            when(milestoneRepository.save(milestone)).thenReturn(milestone);

            service.closeMilestone(user, organizationId, milestoneBoard.getId(), milestone.getId());

            assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.CLOSED);
        }

        @Test
        void close_alreadyClosed_throwsConflictAndDoesNotSave() {
            grantMilestoneAccess();
            milestone.setStatus(MilestoneStatus.CLOSED);

            assertThatThrownBy(() -> service.closeMilestone(user, organizationId, milestoneBoard.getId(), milestone.getId()))
                    .isInstanceOf(ConflictException.class);
            verify(milestoneRepository, never()).save(any());
        }

        @Test
        void reopen_closedMilestone_setsOpen() {
            grantMilestoneAccess();
            milestone.setStatus(MilestoneStatus.CLOSED);
            when(milestoneRepository.save(milestone)).thenReturn(milestone);

            service.reopenMilestone(user, organizationId, milestoneBoard.getId(), milestone.getId());

            assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.OPEN);
        }

        @Test
        void reopen_alreadyOpen_throwsConflict() {
            grantMilestoneAccess();

            assertThatThrownBy(() -> service.reopenMilestone(user, organizationId, milestoneBoard.getId(), milestone.getId()))
                    .isInstanceOf(ConflictException.class);
            verify(milestoneRepository, never()).save(any());
        }
    }

    @Nested
    class Delete {

        @Test
        void deleteMilestone_detachesTicketsThenDeletes() {
            grantMilestoneAccess();

            service.deleteMilestone(user, organizationId, milestoneBoard.getId(), milestone.getId());

            org.mockito.InOrder order = org.mockito.Mockito.inOrder(ticketRepository, milestoneRepository);
            order.verify(ticketRepository).clearMilestoneByMilestoneIds(List.of(milestone.getId()));
            order.verify(milestoneRepository).delete(milestone);
        }

        @Test
        void deleteBoardMilestones_detachesTicketsThenSoftDeletes() {
            UUID boardId = UUID.randomUUID();
            List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
            when(milestoneRepository.findIdsByBoardId(boardId)).thenReturn(ids);

            service.deleteBoardMilestones(boardId);

            org.mockito.InOrder order = org.mockito.Mockito.inOrder(ticketRepository, milestoneRepository);
            order.verify(ticketRepository).clearMilestoneByMilestoneIds(ids);
            order.verify(milestoneRepository).softDeleteAllByBoardId(boardId);
        }

        @Test
        void deleteBoardMilestones_noMilestones_touchesNothing() {
            UUID boardId = UUID.randomUUID();
            when(milestoneRepository.findIdsByBoardId(boardId)).thenReturn(List.of());

            service.deleteBoardMilestones(boardId);

            verify(ticketRepository, never()).clearMilestoneByMilestoneIds(anyList());
            verify(milestoneRepository, never()).softDeleteAllByBoardId(any());
        }
    }

    @Nested
    class Update {

        private MilestoneUpdateRequest update(String name, String description, LocalDate startDate,
                LocalDate targetDate, Boolean clearStartDate, Boolean clearDescription) {
            return new MilestoneUpdateRequest(name, description, startDate, targetDate,
                    clearStartDate, clearDescription, null);
        }

        @Test
        void clearFlags_nullTheFieldsAndKeepTheOthers() {
            grantMilestoneAccess();
            milestone.setStartDate(LocalDate.of(2026, 11, 1));
            milestone.setDescription("Old");
            when(milestoneRepository.save(milestone)).thenReturn(milestone);

            service.updateMilestone(user, organizationId, milestoneBoard.getId(), milestone.getId(),
                    update(null, null, null, null, true, true));

            assertThat(milestone.getStartDate()).isNull();
            assertThat(milestone.getDescription()).isNull();
            assertThat(milestone.getName()).isEqualTo("M");
            assertThat(milestone.getTargetDate()).isEqualTo(LocalDate.of(2026, 12, 1));
            verify(milestoneRepository).save(milestone);
        }

        @Test
        void clearFlags_winOverValuesInTheSameRequest() {
            grantMilestoneAccess();
            when(milestoneRepository.save(milestone)).thenReturn(milestone);

            service.updateMilestone(user, organizationId, milestoneBoard.getId(), milestone.getId(),
                    update(null, "New", LocalDate.of(2026, 11, 1), null, true, true));

            assertThat(milestone.getStartDate()).isNull();
            assertThat(milestone.getDescription()).isNull();
        }

        @Test
        void omittedFields_areUnchanged() {
            grantMilestoneAccess();
            milestone.setStartDate(LocalDate.of(2026, 11, 1));
            milestone.setDescription("Old");
            when(milestoneRepository.save(milestone)).thenReturn(milestone);

            service.updateMilestone(user, organizationId, milestoneBoard.getId(), milestone.getId(),
                    update(null, null, null, LocalDate.of(2026, 12, 20), null, null));

            assertThat(milestone.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(milestone.getDescription()).isEqualTo("Old");
            assertThat(milestone.getTargetDate()).isEqualTo(LocalDate.of(2026, 12, 20));
        }

        @Test
        void targetMovedBeforeExistingStart_throwsAndDoesNotSave() {
            grantMilestoneAccess();
            milestone.setStartDate(LocalDate.of(2026, 11, 1));

            assertThatThrownBy(() -> service.updateMilestone(user, organizationId, milestoneBoard.getId(), milestone.getId(),
                    update(null, null, null, LocalDate.of(2026, 10, 1), null, null)))
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "KAI_VAL_030");
            verify(milestoneRepository, never()).save(any());
        }

    }

    @Nested
    class NamesAreNotUnique {

        @Test
        void create_nameOfExistingMilestoneOnBoard_succeedsWithoutNameLookup() {
            when(boardAccessService.verifyBoardAccess(organizationId, milestoneBoard.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(milestoneBoard);
            when(milestoneRepository.save(any(Milestone.class))).thenAnswer(call -> call.getArgument(0));

            service.createMilestone(user, organizationId, milestoneBoard.getId(),
                    new MilestoneCreateRequest(milestone.getName(), null, null, LocalDate.of(2026, 12, 1), null, null));

            ArgumentCaptor<Milestone> saved = ArgumentCaptor.forClass(Milestone.class);
            verify(milestoneRepository).save(saved.capture());
            assertThat(saved.getValue().getName()).isEqualTo(milestone.getName());
            assertThat(saved.getValue().getStatus()).isEqualTo(MilestoneStatus.OPEN);
            verifyNoMoreInteractions(milestoneRepository);
        }

        @Test
        void update_renameToNameOfOtherMilestone_succeedsWithoutNameLookup() {
            grantMilestoneAccess();
            when(milestoneRepository.save(milestone)).thenReturn(milestone);

            service.updateMilestone(user, organizationId, milestoneBoard.getId(), milestone.getId(),
                    new MilestoneUpdateRequest("Other", null, null, null, null, null, null));

            assertThat(milestone.getName()).isEqualTo("Other");
            verify(milestoneRepository).save(milestone);
            verifyNoMoreInteractions(milestoneRepository);
        }
    }

    @Nested
    class Restore {

        private void grantBoardAccess() {
            when(boardAccessService.verifyBoardAccess(organizationId, milestoneBoard.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(milestoneBoard);
        }

        private Milestone deletedMilestone() {
            Milestone deleted = Milestone.builder().name("M").board(milestoneBoard).deleted(true)
                    .targetDate(LocalDate.of(2026, 12, 1)).build();
            deleted.setId(milestone.getId());
            return deleted;
        }

        @Test
        void deletedMilestone_isRestored() {
            grantBoardAccess();
            when(milestoneRepository.findByIdWithDeleted(milestone.getId()))
                    .thenReturn(java.util.Optional.of(deletedMilestone()));

            service.restoreMilestone(user, organizationId, milestoneBoard.getId(), milestone.getId());

            verify(milestoneRepository).restoreById(milestone.getId());
        }

        @Test
        void notDeleted_throwsValidationAndRestoresNothing() {
            grantBoardAccess();
            when(milestoneRepository.findByIdWithDeleted(milestone.getId()))
                    .thenReturn(java.util.Optional.of(milestone));

            assertThatThrownBy(() -> service.restoreMilestone(user, organizationId, milestoneBoard.getId(), milestone.getId()))
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "KAI_VAL_029");
            verify(milestoneRepository, never()).restoreById(any());
        }
    }

    @Nested
    class Colour {

        private Milestone createWith(String color) {
            when(boardAccessService.verifyBoardAccess(organizationId, milestoneBoard.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(milestoneBoard);
            when(milestoneRepository.save(any(Milestone.class))).thenAnswer(call -> call.getArgument(0));

            service.createMilestone(user, organizationId, milestoneBoard.getId(),
                    new MilestoneCreateRequest("M", null, null, LocalDate.of(2026, 12, 1), color, null));

            ArgumentCaptor<Milestone> saved = ArgumentCaptor.forClass(Milestone.class);
            verify(milestoneRepository).save(saved.capture());
            return saved.getValue();
        }

        @Test
        void create_withoutColour_usesBoardColour() {
            assertThat(createWith(null).getColor()).isEqualTo("#112233");
        }

        @Test
        void create_withColour_keepsIt() {
            assertThat(createWith("#ABCDEF").getColor()).isEqualTo("#ABCDEF");
        }

        @Test
        void update_withColour_replacesIt() {
            grantMilestoneAccess();
            milestone.setColor("#112233");
            when(milestoneRepository.save(milestone)).thenReturn(milestone);

            service.updateMilestone(user, organizationId, milestoneBoard.getId(), milestone.getId(),
                    new MilestoneUpdateRequest(null, null, null, null, null, null, "#00FF00"));

            assertThat(milestone.getColor()).isEqualTo("#00FF00");
        }
    }

    @Nested
    class Dependencies {

        private Milestone other;

        @BeforeEach
        void setUpOther() {
            other = Milestone.builder().name("A").board(milestoneBoard).status(MilestoneStatus.OPEN)
                    .targetDate(LocalDate.of(2026, 11, 1)).build();
            other.setId(UUID.randomUUID());
            grantMilestoneAccess();
            // Lenient: one case replaces the membership to hide the other milestone's board.
            org.mockito.Mockito.lenient().when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId))
                    .thenReturn(List.of(milestoneBoard.getId()));
            when(milestoneRepository.findById(other.getId())).thenReturn(java.util.Optional.of(other));
        }

        @Test
        void add_newPair_savesRow() {
            when(milestoneDependencyRepository.findDependsOnIdsByMilestoneIds(anyCollection())).thenReturn(List.of());

            service.addDependency(user, organizationId, milestoneBoard.getId(), milestone.getId(), other.getId());

            ArgumentCaptor<MilestoneDependency> saved = ArgumentCaptor.forClass(MilestoneDependency.class);
            verify(milestoneDependencyRepository).save(saved.capture());
            assertThat(saved.getValue().getMilestoneId()).isEqualTo(milestone.getId());
            assertThat(saved.getValue().getDependsOnMilestoneId()).isEqualTo(other.getId());
            verify(milestoneHelper).toDetailResponse(milestone, List.of(milestoneBoard.getId()));
        }

        @Test
        void add_existingPair_changesNothing() {
            when(milestoneDependencyRepository.findDependsOnIdsByMilestoneIds(anyCollection())).thenReturn(List.of());
            when(milestoneDependencyRepository.existsByMilestoneIdAndDependsOnMilestoneId(milestone.getId(), other.getId()))
                    .thenReturn(true);

            service.addDependency(user, organizationId, milestoneBoard.getId(), milestone.getId(), other.getId());

            verify(milestoneDependencyRepository, never()).save(any());
        }

        @Test
        void add_dependencyOnInvisibleBoard_throwsNotFound() {
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId)).thenReturn(List.of(UUID.randomUUID()));

            assertThatThrownBy(() -> service.addDependency(user, organizationId, milestoneBoard.getId(), milestone.getId(), other.getId()))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "KAI_NOT_009");
            verify(milestoneDependencyRepository, never()).save(any());
        }

        @Test
        void remove_existingPair_deletesRow() {
            MilestoneDependency row = MilestoneDependency.builder()
                    .milestoneId(milestone.getId()).dependsOnMilestoneId(other.getId()).build();
            when(milestoneDependencyRepository.findByMilestoneIdAndDependsOnMilestoneId(milestone.getId(), other.getId()))
                    .thenReturn(java.util.Optional.of(row));

            service.removeDependency(user, organizationId, milestoneBoard.getId(), milestone.getId(), other.getId());

            verify(milestoneDependencyRepository).delete(row);
        }

        @Test
        void remove_missingPair_throwsNotFound() {
            when(milestoneDependencyRepository.findByMilestoneIdAndDependsOnMilestoneId(milestone.getId(), other.getId()))
                    .thenReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> service.removeDependency(user, organizationId, milestoneBoard.getId(), milestone.getId(), other.getId()))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "KAI_NOT_010");
            verify(milestoneDependencyRepository, never()).delete(any());
        }
    }

    @Nested
    class CreateWithDependencies {

        private Milestone related;
        private Milestone unrelated;

        @BeforeEach
        void setUpDependencies() {
            related = Milestone.builder().name("A").board(milestoneBoard).status(MilestoneStatus.OPEN)
                    .targetDate(LocalDate.of(2026, 11, 1)).build();
            related.setId(UUID.randomUUID());
            Board unrelatedBoard = board(null);
            unrelated = Milestone.builder().name("U").board(unrelatedBoard).status(MilestoneStatus.OPEN)
                    .targetDate(LocalDate.of(2026, 11, 1)).build();
            unrelated.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, milestoneBoard.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(milestoneBoard);
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId))
                    .thenReturn(List.of(milestoneBoard.getId(), unrelatedBoard.getId()));
            // The saved milestone gets its id here, as the database would assign it.
            when(milestoneRepository.save(any(Milestone.class))).thenAnswer(call -> {
                Milestone saved = call.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });
        }

        private MilestoneCreateRequest createRequest(List<UUID> dependsOnIds) {
            return new MilestoneCreateRequest("New", null, null, LocalDate.of(2026, 12, 1), null, dependsOnIds);
        }

        @Test
        void validDependsOnIds_savesEachOnce() {
            when(milestoneRepository.findById(related.getId())).thenReturn(java.util.Optional.of(related));
            when(milestoneDependencyRepository.findDependsOnIdsByMilestoneIds(anyCollection())).thenReturn(List.of());

            service.createMilestone(user, organizationId, milestoneBoard.getId(),
                    createRequest(List.of(related.getId(), related.getId())));

            ArgumentCaptor<Milestone> created = ArgumentCaptor.forClass(Milestone.class);
            verify(milestoneRepository).save(created.capture());
            ArgumentCaptor<MilestoneDependency> saved = ArgumentCaptor.forClass(MilestoneDependency.class);
            verify(milestoneDependencyRepository).save(saved.capture());
            assertThat(saved.getValue().getMilestoneId()).isEqualTo(created.getValue().getId());
            assertThat(saved.getValue().getDependsOnMilestoneId()).isEqualTo(related.getId());
        }

        @Test
        void unrelatedBoardId_throwsAndSavesNoDependency() {
            when(milestoneRepository.findById(related.getId())).thenReturn(java.util.Optional.of(related));
            when(milestoneRepository.findById(unrelated.getId())).thenReturn(java.util.Optional.of(unrelated));
            when(milestoneDependencyRepository.findDependsOnIdsByMilestoneIds(anyCollection())).thenReturn(List.of());

            // The valid id comes first: its row is saved before the rejection, and the
            // transaction rollback of createMilestone discards it with the milestone.
            assertThatThrownBy(() -> service.createMilestone(user, organizationId, milestoneBoard.getId(),
                    createRequest(List.of(related.getId(), unrelated.getId()))))
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "KAI_VAL_035");
            verify(milestoneDependencyRepository, never()).save(org.mockito.ArgumentMatchers.argThat(
                    (MilestoneDependency row) -> unrelated.getId().equals(row.getDependsOnMilestoneId())));
            verify(milestoneHelper, never()).toDetailResponse(any(), anyList());
        }

        @Test
        void unrelatedBoardIdOnly_throwsBeforeAnyDependencyIsSaved() {
            when(milestoneRepository.findById(unrelated.getId())).thenReturn(java.util.Optional.of(unrelated));

            assertThatThrownBy(() -> service.createMilestone(user, organizationId, milestoneBoard.getId(),
                    createRequest(List.of(unrelated.getId()))))
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "KAI_VAL_035");
            verify(milestoneDependencyRepository, never()).save(any());
        }
    }
}
