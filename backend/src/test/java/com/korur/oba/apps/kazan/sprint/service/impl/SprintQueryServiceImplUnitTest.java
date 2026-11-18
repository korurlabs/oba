package com.korur.oba.apps.kazan.sprint.service.impl;

// 1. Framework-level
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardSprint;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.repository.BoardSprintRepository;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintChildBoardResponse;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintDetailResponse;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintListResponse;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintStartConflictResponse;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintStartPreviewResponse;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.entity.SprintSeries;
import com.korur.oba.apps.kazan.sprint.helper.SprintLifecycleHelper;
import com.korur.oba.apps.kazan.sprint.helper.SprintRecurrenceCalculator;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.apps.kazan.sprint.enums.SprintStatus;
import com.korur.oba.apps.kazan.sprint.mapper.SprintMapper;
import com.korur.oba.apps.kazan.sprint.repository.SprintRepository;
import com.korur.oba.dojo.exception.enums.ExceptionNotFoundError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class SprintQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private BoardSprintRepository boardSprintRepository;

    @Mock
    private SprintMapper sprintMapper;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private BoardMemberQueryService boardMemberQueryService;

    @Mock
    private SprintLifecycleHelper sprintLifecycleHelper;

    @Mock
    private TicketRepository ticketRepository;

    @Spy
    private SprintRecurrenceCalculator sprintRecurrenceCalculator = new SprintRecurrenceCalculator();

    @InjectMocks
    private SprintQueryServiceImpl sprintQueryServiceImpl;

    @Nested
    class ListSprints {

        @Test
        void userLacksAccess_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER))
                    .thenThrow(new NotFoundException(ExceptionNotFoundError.RESOURCE_FOUND));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    sprintQueryServiceImpl.listSprints(user, organizationId, boardId));
        }

        @Test
        void userHasAccess_returnsSprintList() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);

            BoardSprint boardSprint = BoardSprint.builder().board(board).sprint(sprint).slug("sprint-1").build();

            SprintListResponse expectedResponse = new SprintListResponse(
                    sprintId, "Sprint 1", "sprint-1", SprintStatus.PLANNED, null, null, null, null);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER)).thenReturn(board);
            when(boardSprintRepository.findByBoardIdWithLiveSprint(boardId)).thenReturn(List.of(boardSprint));
            when(sprintMapper.toListResponse(sprint, "sprint-1", null)).thenReturn(expectedResponse);

            // Act
            List<SprintListResponse> result = sprintQueryServiceImpl.listSprints(user, organizationId, boardId);

            // Assert
            assertNotNull(result);
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);
            verify(boardSprintRepository).findByBoardIdWithLiveSprint(boardId);
        }
    }

    @Nested
    class GetSprint {

        @Test
        void userLacksAccess_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.VIEWER))
                    .thenThrow(new NotFoundException(ExceptionNotFoundError.RESOURCE_FOUND));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    sprintQueryServiceImpl.getSprint(user, organizationId, boardId, sprintId));
        }

        @Test
        void userHasAccess_returnsSprintDetail() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID sprintId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Sprint sprint = Sprint.builder().name("Sprint 1").build();
            sprint.setId(sprintId);

            SprintDetailResponse expectedResponse = new SprintDetailResponse(
                    sprintId, null, null, "Sprint 1", null, null, null, SprintStatus.PLANNED, null, null, null);

            when(boardAccessService.verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.VIEWER)).thenReturn(sprint);
            when(sprintMapper.toDetailResponse(sprint)).thenReturn(expectedResponse);

            // Act
            SprintDetailResponse result = sprintQueryServiceImpl.getSprint(user, organizationId, boardId, sprintId);

            // Assert
            assertNotNull(result);
            verify(boardAccessService).verifySprintAccess(organizationId, boardId, sprintId, user.getId(), BoardMemberRole.VIEWER);
        }
    }

    @Nested
    class GetActiveSprintIdsByBoardIds {

        @Test
        void emptyBoardIds_returnsEmptyListWithoutQuerying() {
            // Act
            List<UUID> result = sprintQueryServiceImpl.getActiveSprintIdsByBoardIds(List.of());

            // Assert
            assertTrue(result.isEmpty());
            verifyNoInteractions(sprintRepository);
        }

        @Test
        void boardIds_returnsActiveSprintIdsOfThoseBoards() {
            // Arrange
            List<UUID> boardIds = List.of(UUID.randomUUID(), UUID.randomUUID());
            List<UUID> sprintIds = List.of(UUID.randomUUID());
            when(sprintRepository.findIdsByBoardIdsAndStatus(boardIds, SprintStatus.ACTIVE)).thenReturn(sprintIds);

            // Act
            List<UUID> result = sprintQueryServiceImpl.getActiveSprintIdsByBoardIds(boardIds);

            // Assert
            assertEquals(sprintIds, result);
        }
    }

    @Nested
    class ListChildBoards {

        private User user() {
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            return user;
        }

        private Board board(String name, Board parent) {
            Board board = Board.builder().name(name).prefix(name.substring(0, 1)).parentBoard(parent).build();
            board.setId(UUID.randomUUID());
            if (parent != null) {
                parent.getChildBoards().add(board);
            }
            return board;
        }

        private void mapperBuildsRecords() {
            when(sprintMapper.toChildBoardResponse(any(Board.class), any(), anyInt(), anyBoolean(), any(), any()))
                    .thenAnswer(invocation -> {
                        Board board = invocation.getArgument(0);
                        Sprint active = invocation.getArgument(4);
                        return new SprintChildBoardResponse(board.getId(), board.getName(), board.getPrefix(), null,
                                invocation.getArgument(1), invocation.getArgument(2),
                                invocation.getArgument(3),
                                active == null ? null : active.getId(),
                                active == null ? null : active.getName(), invocation.getArgument(5));
                    });
        }

        @Test
        void userLacksAdminAccess_throwsAndReadsNothingElse() {
            UUID boardId = UUID.randomUUID();
            User user = user();
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(new NotFoundException(ExceptionNotFoundError.RESOURCE_FOUND));

            assertThrows(NotFoundException.class, () -> sprintQueryServiceImpl.listChildBoards(user, organizationId, boardId));

            verifyNoInteractions(boardMemberQueryService, sprintMapper);
        }

        @Test
        void noChildBoards_returnsEmptyList() {
            User user = user();
            Board root = board("Root", null);
            when(boardAccessService.verifyBoardAccess(organizationId, root.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(root);
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId)).thenReturn(List.of(root.getId()));
            when(boardMemberQueryService.getAdminBoardIds(user.getId(), organizationId)).thenReturn(List.of(root.getId()));

            assertTrue(sprintQueryServiceImpl.listChildBoards(user, organizationId, root.getId()).isEmpty());
        }

        @Test
        void nestedChildren_areListedDepthFirstWithDepthAndSortedByName() {
            User user = user();
            Board root = board("Root", null);
            Board zeta = board("Zeta", root);
            Board alpha = board("alpha", root);
            Board alphaChild = board("Alpha child", alpha);
            Board alphaGrandchild = board("Alpha grandchild", alphaChild);
            when(boardAccessService.verifyBoardAccess(organizationId, root.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(root);
            List<UUID> all = List.of(root.getId(), zeta.getId(), alpha.getId(), alphaChild.getId(),
                    alphaGrandchild.getId());
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId)).thenReturn(all);
            when(boardMemberQueryService.getAdminBoardIds(user.getId(), organizationId)).thenReturn(all);
            when(sprintRepository.findFirstByBoardSprints_Board_IdAndStatusOrderByStartDateDesc(
                    any(UUID.class), any(SprintStatus.class))).thenReturn(Optional.empty());
            mapperBuildsRecords();

            List<SprintChildBoardResponse> result = sprintQueryServiceImpl.listChildBoards(user, organizationId, root.getId());

            assertEquals(List.of(alpha.getId(), alphaChild.getId(), alphaGrandchild.getId(), zeta.getId()),
                    result.stream().map(SprintChildBoardResponse::id).toList());
            assertEquals(List.of(1, 2, 3, 1), result.stream().map(SprintChildBoardResponse::depth).toList());
            assertEquals(alphaChild.getId(), result.get(2).parentBoardId());
        }

        @Test
        void nonMemberBoard_isHiddenButItsMemberGrandchildStaysListed() {
            User user = user();
            Board root = board("Root", null);
            Board hidden = board("Hidden", root);
            Board visibleGrandchild = board("Visible", hidden);
            when(boardAccessService.verifyBoardAccess(organizationId, root.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(root);
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId))
                    .thenReturn(List.of(root.getId(), visibleGrandchild.getId()));
            when(boardMemberQueryService.getAdminBoardIds(user.getId(), organizationId)).thenReturn(List.of(root.getId()));
            when(sprintRepository.findFirstByBoardSprints_Board_IdAndStatusOrderByStartDateDesc(
                    visibleGrandchild.getId(), SprintStatus.ACTIVE)).thenReturn(Optional.empty());
            mapperBuildsRecords();

            List<SprintChildBoardResponse> result = sprintQueryServiceImpl.listChildBoards(user, organizationId, root.getId());

            assertEquals(1, result.size());
            assertEquals(visibleGrandchild.getId(), result.get(0).id());
            assertEquals(2, result.get(0).depth());
            assertNull(result.get(0).parentBoardId());
            verify(sprintRepository, org.mockito.Mockito.never())
                    .findFirstByBoardSprints_Board_IdAndStatusOrderByStartDateDesc(hidden.getId(), SprintStatus.ACTIVE);
        }

        @Test
        void visibleParents_keepTheirIds() {
            User user = user();
            Board root = board("Root", null);
            Board child = board("Child", root);
            Board grandchild = board("Grandchild", child);
            when(boardAccessService.verifyBoardAccess(organizationId, root.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(root);
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId))
                    .thenReturn(List.of(root.getId(), child.getId(), grandchild.getId()));
            when(boardMemberQueryService.getAdminBoardIds(user.getId(), organizationId)).thenReturn(List.of(root.getId()));
            when(sprintRepository.findFirstByBoardSprints_Board_IdAndStatusOrderByStartDateDesc(
                    any(UUID.class), any(SprintStatus.class))).thenReturn(Optional.empty());
            mapperBuildsRecords();

            List<SprintChildBoardResponse> result = sprintQueryServiceImpl.listChildBoards(user, organizationId, root.getId());

            assertEquals(List.of(child.getId(), grandchild.getId()),
                    result.stream().map(SprintChildBoardResponse::id).toList());
            assertEquals(root.getId(), result.get(0).parentBoardId());
            assertEquals(child.getId(), result.get(1).parentBoardId());
        }

        @Test
        void memberRoleOnChild_canLinkIsFalse() {
            User user = user();
            Board root = board("Root", null);
            Board adminChild = board("Admin child", root);
            Board memberChild = board("Member child", root);
            when(boardAccessService.verifyBoardAccess(organizationId, root.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(root);
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId))
                    .thenReturn(List.of(root.getId(), adminChild.getId(), memberChild.getId()));
            when(boardMemberQueryService.getAdminBoardIds(user.getId(), organizationId))
                    .thenReturn(List.of(root.getId(), adminChild.getId()));
            when(sprintRepository.findFirstByBoardSprints_Board_IdAndStatusOrderByStartDateDesc(
                    any(UUID.class), any(SprintStatus.class))).thenReturn(Optional.empty());
            mapperBuildsRecords();

            List<SprintChildBoardResponse> result = sprintQueryServiceImpl.listChildBoards(user, organizationId, root.getId());

            assertTrue(result.get(0).canLink());
            assertEquals(adminChild.getId(), result.get(0).id());
            assertEquals(memberChild.getId(), result.get(1).id());
            assertFalse(result.get(1).canLink());
        }

        @Test
        void childWithActiveSprint_carriesActiveSprintIdAndName() {
            User user = user();
            Board root = board("Root", null);
            Board busy = board("Busy", root);
            Board idle = board("Idle", root);
            Sprint active = Sprint.builder().name("Sprint 7").status(SprintStatus.ACTIVE).build();
            active.setId(UUID.randomUUID());
            when(boardAccessService.verifyBoardAccess(organizationId, root.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(root);
            List<UUID> all = List.of(root.getId(), busy.getId(), idle.getId());
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId)).thenReturn(all);
            when(boardMemberQueryService.getAdminBoardIds(user.getId(), organizationId)).thenReturn(all);
            when(sprintRepository.findFirstByBoardSprints_Board_IdAndStatusOrderByStartDateDesc(
                    busy.getId(), SprintStatus.ACTIVE)).thenReturn(Optional.of(active));
            when(sprintRepository.findFirstByBoardSprints_Board_IdAndStatusOrderByStartDateDesc(
                    idle.getId(), SprintStatus.ACTIVE)).thenReturn(Optional.empty());
            mapperBuildsRecords();

            List<SprintChildBoardResponse> result = sprintQueryServiceImpl.listChildBoards(user, organizationId, root.getId());

            assertEquals(active.getId(), result.get(0).activeSprintId());
            assertEquals("Sprint 7", result.get(0).activeSprintName());
            assertNull(result.get(1).activeSprintId());
            assertNull(result.get(1).activeSprintName());
        }

        @Test
        void cyclicHierarchy_isWalkedOnce() {
            User user = user();
            Board root = board("Root", null);
            Board child = board("Child", root);
            child.getChildBoards().add(root);
            when(boardAccessService.verifyBoardAccess(organizationId, root.getId(), user.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(root);
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId))
                    .thenReturn(List.of(root.getId(), child.getId()));
            when(boardMemberQueryService.getAdminBoardIds(user.getId(), organizationId)).thenReturn(List.of());
            when(sprintRepository.findFirstByBoardSprints_Board_IdAndStatusOrderByStartDateDesc(
                    child.getId(), SprintStatus.ACTIVE)).thenReturn(Optional.empty());
            mapperBuildsRecords();

            assertEquals(1, sprintQueryServiceImpl.listChildBoards(user, organizationId, root.getId()).size());
        }
    }

    @Nested
    class GetStartPreview {

        private final User user = userWithId();
        private final Board parent = board("Parent");
        private final Board child = board("Child");
        private final Board outside = board("Outside");
        private final Board hidden = board("Hidden");
        private Sprint starting;

        private User userWithId() {
            User created = User.builder().build();
            created.setId(UUID.randomUUID());
            return created;
        }

        private Board board(String name) {
            Board created = Board.builder().name(name).prefix(name.substring(0, 1)).build();
            created.setId(UUID.randomUUID());
            return created;
        }

        private Sprint sprintOn(SprintStatus status, int number, Board... boards) {
            Sprint created = Sprint.builder().name("Sprint " + number).status(status).build();
            created.setId(UUID.randomUUID());
            // A higher number is created later, so ties on the shared board keep the number order.
            ReflectionTestUtils.setField(created, "createdAt", Instant.EPOCH.plusSeconds(number));
            for (Board board : boards) {
                created.getBoardSprints().add(BoardSprint.builder().board(board).sprint(created).build());
            }
            return created;
        }

        private Ticket ticketOn(Board board, Sprint sprint) {
            return Ticket.builder().board(board).sprint(sprint).status(TicketStatus.IN_PROGRESS).build();
        }

        private void arrange(Set<Board> members, Set<Board> admins, Sprint... blocking) {
            starting = sprintOn(SprintStatus.PLANNED, 9, parent, child);
            when(boardAccessService.verifySprintAccess(organizationId, parent.getId(), starting.getId(), user.getId(),
                    BoardMemberRole.ADMIN)).thenReturn(starting);
            when(boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId))
                    .thenReturn(members.stream().map(Board::getId).toList());
            when(boardMemberQueryService.getAdminBoardIds(user.getId(), organizationId))
                    .thenReturn(admins.stream().map(Board::getId).toList());
            when(sprintLifecycleHelper.findOtherActiveSprintsOnLinkedBoards(starting)).thenReturn(List.of(blocking));
            org.mockito.Mockito.lenient().when(sprintMapper.toStartConflictResponse(
                    any(Board.class), any(Sprint.class), org.mockito.ArgumentMatchers.anyLong(), anyBoolean(), anyBoolean(),
                    any()))
                    .thenAnswer(invocation -> {
                        Board board = invocation.getArgument(0);
                        Sprint sprint = invocation.getArgument(1);
                        return new SprintStartConflictResponse(board.getId(), board.getName(), board.getPrefix(),
                                sprint.getId(), sprint.getName(), invocation.<Long>getArgument(2),
                                invocation.<Boolean>getArgument(3), invocation.<Boolean>getArgument(4),
                                invocation.getArgument(5));
                    });
        }

        @Test
        void noBlockingSprint_returnsNoConflictsAndCanTakeOver() {
            arrange(Set.of(parent, child), Set.of(parent, child));

            SprintStartPreviewResponse result = sprintQueryServiceImpl.getStartPreview(user, organizationId, parent.getId(), starting.getId());

            assertTrue(result.canTakeOver());
            assertTrue(result.conflicts().isEmpty());
        }

        @Test
        void sharedBlockingSprint_countsUnfinishedTicketsPerBoardAndMovesNothingToBacklog() {
            Sprint blocking = sprintOn(SprintStatus.ACTIVE, 3, parent, child);
            arrange(Set.of(parent, child), Set.of(parent, child), blocking);
            when(ticketRepository.findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE)).thenReturn(List.of(
                    ticketOn(parent, blocking), ticketOn(parent, blocking), ticketOn(child, blocking)));

            SprintStartPreviewResponse result = sprintQueryServiceImpl.getStartPreview(user, organizationId, parent.getId(), starting.getId());

            assertTrue(result.canTakeOver());
            assertEquals(2, result.conflicts().size());
            SprintStartConflictResponse parentRow = result.conflicts().get(0);
            SprintStartConflictResponse childRow = result.conflicts().get(1);
            assertEquals(parent.getId(), parentRow.boardId());
            assertEquals(2L, parentRow.unfinishedTicketCount());
            assertFalse(parentRow.movesToBacklog());
            assertTrue(parentRow.canManage());
            assertEquals(child.getId(), childRow.boardId());
            assertEquals(1L, childRow.unfinishedTicketCount());
            assertEquals(blocking.getId(), childRow.sprintId());
        }

        @Test
        void boardNotLinkedToStartingSprint_movesToBacklogAndComesLast() {
            Sprint blocking = sprintOn(SprintStatus.ACTIVE, 3, outside, child);
            arrange(Set.of(parent, child, outside), Set.of(parent, child, outside), blocking);
            when(ticketRepository.findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE))
                    .thenReturn(List.of(ticketOn(outside, blocking)));

            SprintStartPreviewResponse result = sprintQueryServiceImpl.getStartPreview(user, organizationId, parent.getId(), starting.getId());

            assertEquals(List.of(child.getId(), outside.getId()),
                    result.conflicts().stream().map(SprintStartConflictResponse::boardId).toList());
            assertFalse(result.conflicts().get(0).movesToBacklog());
            assertEquals(0L, result.conflicts().get(0).unfinishedTicketCount());
            assertTrue(result.conflicts().get(1).movesToBacklog());
            assertEquals(1L, result.conflicts().get(1).unfinishedTicketCount());
        }

        @Test
        void memberWithoutAdminRole_marksRowNotManageableAndCannotTakeOver() {
            Sprint blocking = sprintOn(SprintStatus.ACTIVE, 3, parent, child);
            arrange(Set.of(parent, child), Set.of(parent), blocking);
            when(ticketRepository.findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE)).thenReturn(List.of());

            SprintStartPreviewResponse result = sprintQueryServiceImpl.getStartPreview(user, organizationId, parent.getId(), starting.getId());

            assertFalse(result.canTakeOver());
            assertTrue(result.conflicts().get(0).canManage());
            assertFalse(result.conflicts().get(1).canManage());
        }

        @Test
        void nonMemberBoard_isHiddenButCannotTakeOver() {
            Sprint blocking = sprintOn(SprintStatus.ACTIVE, 3, parent, hidden);
            arrange(Set.of(parent, child), Set.of(parent, child), blocking);
            when(ticketRepository.findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE)).thenReturn(List.of());

            SprintStartPreviewResponse result = sprintQueryServiceImpl.getStartPreview(user, organizationId, parent.getId(), starting.getId());

            assertFalse(result.canTakeOver());
            assertEquals(List.of(parent.getId()),
                    result.conflicts().stream().map(SprintStartConflictResponse::boardId).toList());
        }

        @Test
        void twoBlockingSprints_areOrderedByTheLinkOrderOfTheStartingSprint() {
            Sprint onChild = sprintOn(SprintStatus.ACTIVE, 2, child);
            Sprint onParent = sprintOn(SprintStatus.ACTIVE, 5, parent);
            arrange(Set.of(parent, child), Set.of(parent, child), onChild, onParent);
            when(ticketRepository.findBySprint_IdAndStatusNot(any(), any())).thenReturn(List.of());

            SprintStartPreviewResponse result = sprintQueryServiceImpl.getStartPreview(user, organizationId, parent.getId(), starting.getId());

            assertEquals(List.of(parent.getId(), child.getId()),
                    result.conflicts().stream().map(SprintStartConflictResponse::boardId).toList());
        }

        private SprintSeries seriesOf(boolean enabled, Sprint... sprints) {
            SprintSeries series = SprintSeries.builder().name("Sprint").nextRound(3).enabled(enabled).build();
            series.setId(UUID.randomUUID());
            for (Sprint sprint : sprints) {
                ReflectionTestUtils.setField(sprint, "series", series);
            }
            org.mockito.Mockito.lenient().when(sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId()))
                    .thenReturn(List.of(sprints));
            return series;
        }

        @Test
        void blockingSprintIsTheLastOfAnEnabledSeries_namesTheSeriesThatStops() {
            Sprint blocking = sprintOn(SprintStatus.ACTIVE, 3, parent);
            seriesOf(true, blocking);
            arrange(Set.of(parent, child), Set.of(parent, child), blocking);
            when(ticketRepository.findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE)).thenReturn(List.of());

            SprintStartPreviewResponse result = sprintQueryServiceImpl.getStartPreview(user, organizationId, parent.getId(), starting.getId());

            assertEquals("Sprint", result.conflicts().get(0).endsSeriesName());
        }

        @Test
        void blockingSeriesHasItsNextSprintPlanned_namesNoSeries() {
            Sprint ahead = sprintOn(SprintStatus.PLANNED, 4, parent);
            Sprint blocking = sprintOn(SprintStatus.ACTIVE, 3, parent);
            seriesOf(true, ahead, blocking);
            arrange(Set.of(parent, child), Set.of(parent, child), blocking);
            when(ticketRepository.findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE)).thenReturn(List.of());

            SprintStartPreviewResponse result = sprintQueryServiceImpl.getStartPreview(user, organizationId, parent.getId(), starting.getId());

            assertNull(result.conflicts().get(0).endsSeriesName());
        }

        @Test
        void blockingSeriesIsSwitchedOff_namesNoSeries() {
            Sprint blocking = sprintOn(SprintStatus.ACTIVE, 3, parent);
            seriesOf(false, blocking);
            arrange(Set.of(parent, child), Set.of(parent, child), blocking);
            when(ticketRepository.findBySprint_IdAndStatusNot(blocking.getId(), TicketStatus.DONE)).thenReturn(List.of());

            SprintStartPreviewResponse result = sprintQueryServiceImpl.getStartPreview(user, organizationId, parent.getId(), starting.getId());

            assertNull(result.conflicts().get(0).endsSeriesName());
        }

        @Test
        void userLacksAdminRole_throwsAndReadsNothing() {
            UUID sprintId = UUID.randomUUID();
            when(boardAccessService.verifySprintAccess(organizationId, parent.getId(), sprintId, user.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(new NotFoundException(ExceptionNotFoundError.RESOURCE_FOUND));

            assertThrows(NotFoundException.class,
                    () -> sprintQueryServiceImpl.getStartPreview(user, organizationId, parent.getId(), sprintId));

            verifyNoInteractions(sprintLifecycleHelper, ticketRepository);
        }
    }
}
