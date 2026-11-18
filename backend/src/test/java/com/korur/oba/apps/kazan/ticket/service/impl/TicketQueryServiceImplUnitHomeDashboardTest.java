package com.korur.oba.apps.kazan.ticket.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.dto.response.BoardListResponse;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardSprint;
import com.korur.oba.apps.kazan.board.helper.BoardHelper;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.service.SprintQueryService;
import com.korur.oba.apps.kazan.ticket.dto.projection.LeaderboardTicketProjection;
import com.korur.oba.apps.kazan.ticket.dto.projection.SprintListRootProjection;
import com.korur.oba.apps.kazan.ticket.dto.projection.StaleBacklogTicketProjection;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeBurndownPointResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeLeaderboardEntryResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeLeaderboardResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeSprintResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeStaleBacklogResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeStaleTicketResponse;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.entity.TicketAssignee;
import com.korur.oba.apps.kazan.ticket.enums.HomeLeaderboardPeriod;
import com.korur.oba.apps.kazan.ticket.enums.MissionRank;
import com.korur.oba.apps.kazan.ticket.enums.TicketPriority;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.mapper.TicketMapper;
import com.korur.oba.apps.kazan.ticket.repository.TicketAssigneeRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.apps.kazan.ticket.validator.TicketValidator;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.time.BusinessCalendar;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Unit tests for the home dashboard reads of the ticket query service: stale backlog,
 * leaderboard, focus board and burndown.
 */
@ExtendWith(MockitoExtension.class)
class TicketQueryServiceImplUnitHomeDashboardTest {

    private final UUID organizationId = UUID.randomUUID();

    private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketAssigneeRepository ticketAssigneeRepository;

    @Mock
    private TicketValidator ticketValidator;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private BoardHelper boardHelper;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private BoardMemberQueryService boardMemberQueryService;

    @Mock
    private SprintQueryService sprintQueryService;

    @Mock
    private UserQueryService userQueryService;

    private final UUID userId = UUID.randomUUID();

    private TicketQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = newService(NOW);
    }

    private TicketQueryServiceImpl newService(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new TicketQueryServiceImpl(ticketRepository, ticketAssigneeRepository, ticketValidator, ticketMapper,
                boardHelper, boardAccessService, boardMemberQueryService, sprintQueryService, userQueryService,
                new LoggingService(), clock, new BusinessCalendar(clock));
    }

    private static Board board(String name, Board parent) {
        Board board = Board.builder().name(name).slug(name.toLowerCase()).parentBoard(parent).build();
        board.setId(UUID.randomUUID());
        return board;
    }

    private static Sprint sprint(String name, Instant start, Instant end, Board... boards) {
        Sprint sprint = Sprint.builder().name(name).startDate(start).endDate(end).build();
        sprint.setId(UUID.randomUUID());
        for (Board board : boards) {
            sprint.getBoardSprints().add(BoardSprint.builder().board(board).sprint(sprint).build());
        }
        return sprint;
    }

    private static User user(String lastName) {
        User user = User.builder().email(lastName.toLowerCase() + "@example.com")
                .profile(UserProfile.builder().firstName("F").lastName(lastName).build()).build();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static Ticket ticket(MissionRank rank) {
        Ticket ticket = Ticket.builder().missionRank(rank).status(TicketStatus.DONE).build();
        ticket.setId(UUID.randomUUID());
        return ticket;
    }

    private static TicketAssignee assignment(User user, Ticket ticket) {
        return TicketAssignee.builder().user(user).ticket(ticket).build();
    }

    private static SprintListRootProjection root(String status, Instant completedAt) {
        SprintListRootProjection root = mock(SprintListRootProjection.class);
        when(root.getStatus()).thenReturn(status);
        lenientCompletedAt(root, completedAt);
        return root;
    }

    private static void lenientCompletedAt(SprintListRootProjection root, Instant completedAt) {
        org.mockito.Mockito.lenient().when(root.getCompletedAt()).thenReturn(completedAt);
    }

    private static LeaderboardTicketProjection row(User user, MissionRank rank, long assigneeCount) {
        // Lenient: only the first row of a user supplies the name and email.
        LeaderboardTicketProjection row = mock(LeaderboardTicketProjection.class, withSettings().strictness(Strictness.LENIENT));
        when(row.getUserId()).thenReturn(user.getId());
        when(row.getFirstName()).thenReturn(user.getProfile().getFirstName());
        when(row.getLastName()).thenReturn(user.getProfile().getLastName());
        when(row.getEmail()).thenReturn(user.getEmail());
        when(row.getMissionRank()).thenReturn(rank.name());
        when(row.getAssigneeCount()).thenReturn(assigneeCount);
        return row;
    }

    @Nested
    class StaleBacklog {

        @Test
        void userWithoutBoards_returnsEmptyWithoutQuery() {
            // Arrange
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of());

            // Act
            HomeStaleBacklogResponse result = service.getStaleBacklog(userId, organizationId);

            // Assert
            assertThat(result.total()).isZero();
            assertThat(result.tickets()).isEmpty();
            verifyNoInteractions(ticketRepository);
        }

        @Test
        void nothingStale_skipsTheListQuery() {
            // Arrange
            List<UUID> boardIds = List.of(UUID.randomUUID());
            Instant cutoff = NOW.minusSeconds(30L * 24 * 3600);
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(boardIds);
            when(ticketRepository.countStaleBacklogTickets(boardIds, cutoff)).thenReturn(0L);

            // Act
            HomeStaleBacklogResponse result = service.getStaleBacklog(userId, organizationId);

            // Assert
            assertThat(result.total()).isZero();
            assertThat(result.tickets()).isEmpty();
            verify(ticketRepository, never()).findStaleBacklogTickets(anyCollection(), any(), anyInt());
        }

        @Test
        void staleTickets_mapRowsAndSetCanDeleteFromContributorBoards() {
            // Arrange
            UUID memberBoard = UUID.randomUUID();
            UUID viewerBoard = UUID.randomUUID();
            List<UUID> boardIds = List.of(memberBoard, viewerBoard);
            Instant cutoff = NOW.minusSeconds(30L * 24 * 3600);
            Instant activity = Instant.parse("2026-08-01T00:00:00Z");
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(boardIds);
            when(boardMemberQueryService.getContributorBoardIds(userId, organizationId)).thenReturn(List.of(memberBoard));
            when(ticketRepository.countStaleBacklogTickets(boardIds, cutoff)).thenReturn(120L);
            StaleBacklogTicketProjection editable = staleRow("OBA-1", memberBoard, activity);
            StaleBacklogTicketProjection readOnly = staleRow("OBA-2", viewerBoard, activity);
            when(ticketRepository.findStaleBacklogTickets(boardIds, cutoff, 50))
                    .thenReturn(List.of(editable, readOnly));

            // Act
            HomeStaleBacklogResponse result = service.getStaleBacklog(userId, organizationId);

            // Assert
            assertThat(result.total()).isEqualTo(120L);
            assertThat(result.tickets()).hasSize(2);
            HomeStaleTicketResponse first = result.tickets().get(0);
            assertThat(first.key()).isEqualTo("OBA-1");
            assertThat(first.status()).isEqualTo(TicketStatus.TODO);
            assertThat(first.priority()).isEqualTo(TicketPriority.HIGH);
            assertThat(first.missionRank()).isEqualTo(MissionRank.L);
            assertThat(first.lastActivityAt()).isEqualTo(activity);
            assertThat(first.canDelete()).isTrue();
            assertThat(result.tickets().get(1).canDelete()).isFalse();
        }

        private StaleBacklogTicketProjection staleRow(String key, UUID boardId, Instant activity) {
            StaleBacklogTicketProjection row = mock(StaleBacklogTicketProjection.class);
            when(row.getId()).thenReturn(UUID.randomUUID());
            when(row.getKey()).thenReturn(key);
            when(row.getTitle()).thenReturn("Title " + key);
            when(row.getStatus()).thenReturn("TODO");
            when(row.getPriority()).thenReturn("HIGH");
            when(row.getMissionRank()).thenReturn("L");
            when(row.getBoardId()).thenReturn(boardId);
            when(row.getBoardName()).thenReturn("Board");
            when(row.getBoardSlug()).thenReturn("board");
            when(row.getLastActivityAt()).thenReturn(activity);
            return row;
        }
    }

    @Nested
    class Leaderboard {

        private final List<UUID> boardIds = List.of(UUID.randomUUID());
        private final List<UUID> sprintIds = List.of(UUID.randomUUID());

        private void activeSprintExists() {
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(boardIds);
            when(sprintQueryService.getActiveSprintIdsByBoardIds(boardIds)).thenReturn(sprintIds);
        }

        private void assignmentsReturned(User... members) {
            List<UUID> memberIds = new ArrayList<>();
            for (User member : members) {
                memberIds.add(member.getId());
            }
            when(boardMemberQueryService.getMemberUserIds(boardIds)).thenReturn(memberIds);
        }

        private void assignees(List<TicketAssignee> assignments, User... users) {
            when(ticketAssigneeRepository.findByAssigneesBoardsSprintsAndStatus(any(), eq(boardIds), eq(sprintIds),
                    eq(TicketStatus.DONE))).thenReturn(assignments);
            when(userQueryService.getUserEntitiesByIds(any())).thenReturn(List.of(users));
        }

        @ParameterizedTest
        @CsvSource({"XS,1.0", "S,2.0", "M,3.0", "L,5.0", "XL,8.0", "XXL,13.0"})
        void sprintPeriod_singleAssignee_earnsFullRankPoints(MissionRank rank, String expected) {
            // Arrange
            User alice = user("Alice");
            activeSprintExists();
            assignmentsReturned(alice);
            assignees(List.of(assignment(alice, ticket(rank))), alice);

            // Act
            HomeLeaderboardResponse result = service.getLeaderboard(userId, organizationId, HomeLeaderboardPeriod.SPRINT);

            // Assert
            assertThat(result.period()).isEqualTo(HomeLeaderboardPeriod.SPRINT);
            assertThat(result.from()).isNull();
            assertThat(result.to()).isNull();
            assertThat(result.entries()).hasSize(1);
            assertThat(result.entries().get(0).points()).isEqualByComparingTo(new BigDecimal(expected));
            assertThat(result.entries().get(0).tickets()).isEqualTo(1);
            verify(ticketAssigneeRepository, times(1)).findByAssigneesBoardsSprintsAndStatus(any(), any(), any(),
                    any());
            verify(ticketRepository, never()).findCompletedTicketAssignments(any(), any(), any());
        }

        @Test
        void sprintPeriod_ticketWithTwoAssignees_countsHalfToEach() {
            // Arrange
            User alice = user("Alice");
            User bob = user("Bob");
            Ticket shared = ticket(MissionRank.M);
            activeSprintExists();
            assignmentsReturned(alice, bob);
            assignees(List.of(assignment(alice, shared), assignment(bob, shared)), alice, bob);

            // Act
            HomeLeaderboardResponse result = service.getLeaderboard(userId, organizationId, HomeLeaderboardPeriod.SPRINT);

            // Assert
            assertThat(result.entries()).extracting(HomeLeaderboardEntryResponse::points)
                    .allSatisfy(points -> assertThat(points).isEqualByComparingTo("1.5"));
            assertThat(result.entries()).extracting(HomeLeaderboardEntryResponse::tickets).containsOnly(1);
        }

        @Test
        void sprintPeriod_threeThirdsOfSmallTickets_addUpToOneWholeTicket() {
            // Arrange
            User alice = user("Alice");
            User bob = user("Bob");
            User carol = user("Carol");
            List<TicketAssignee> assignments = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Ticket small = ticket(MissionRank.XS);
                assignments.add(assignment(alice, small));
                assignments.add(assignment(bob, small));
                assignments.add(assignment(carol, small));
            }
            activeSprintExists();
            assignmentsReturned(alice, bob, carol);
            assignees(assignments, alice, bob, carol);

            // Act
            HomeLeaderboardResponse result = service.getLeaderboard(userId, organizationId, HomeLeaderboardPeriod.SPRINT);

            // Assert: 3 x 1/3 is 1.0, not 0.9 from rounding each share
            assertThat(result.entries()).extracting(HomeLeaderboardEntryResponse::points)
                    .allSatisfy(points -> assertThat(points).isEqualByComparingTo("1.0"));
        }

        @Test
        void sprintPeriod_thirteenPointsAcrossThree_roundsTotalToOneDecimal() {
            // Arrange
            User alice = user("Alice");
            User bob = user("Bob");
            User carol = user("Carol");
            Ticket huge = ticket(MissionRank.XXL);
            activeSprintExists();
            assignmentsReturned(alice, bob, carol);
            assignees(List.of(assignment(alice, huge), assignment(bob, huge), assignment(carol, huge)),
                    alice, bob, carol);

            // Act
            HomeLeaderboardResponse result = service.getLeaderboard(userId, organizationId, HomeLeaderboardPeriod.SPRINT);

            // Assert
            assertThat(result.entries()).extracting(HomeLeaderboardEntryResponse::points)
                    .allSatisfy(points -> assertThat(points).isEqualByComparingTo("4.3"));
        }

        @Test
        void sprintPeriod_mixedUsers_ranksByPointsThenTicketsThenLastName() {
            // Arrange
            User zed = user("Zed");
            User amy = user("amy");
            User big = user("Big");
            User tie = user("Tie");
            List<TicketAssignee> assignments = new ArrayList<>(List.of(
                    assignment(big, ticket(MissionRank.XL)),
                    assignment(zed, ticket(MissionRank.S)), assignment(zed, ticket(MissionRank.S)),
                    assignment(amy, ticket(MissionRank.S)), assignment(amy, ticket(MissionRank.S)),
                    assignment(tie, ticket(MissionRank.XL))));
            activeSprintExists();
            assignmentsReturned(zed, amy, big, tie);
            assignees(assignments, zed, amy, big, tie);

            // Act
            HomeLeaderboardResponse result = service.getLeaderboard(userId, organizationId, HomeLeaderboardPeriod.SPRINT);

            // Assert: Big and Tie 8.0 with 1 ticket (last name), then amy and Zed 4.0 with 2 tickets
            assertThat(result.entries()).extracting(HomeLeaderboardEntryResponse::lastName)
                    .containsExactly("Big", "Tie", "amy", "Zed");
        }

        @Test
        void sprintPeriod_noFinishedTickets_returnsNoEntries() {
            // Arrange
            User alice = user("Alice");
            activeSprintExists();
            assignmentsReturned(alice);
            when(ticketAssigneeRepository.findByAssigneesBoardsSprintsAndStatus(any(), any(), any(), any()))
                    .thenReturn(List.of());

            // Act
            HomeLeaderboardResponse result = service.getLeaderboard(userId, organizationId, HomeLeaderboardPeriod.SPRINT);

            // Assert
            assertThat(result.period()).isEqualTo(HomeLeaderboardPeriod.SPRINT);
            assertThat(result.entries()).isEmpty();
        }

        @Test
        void sprintPeriod_noActiveSprint_fallsBackToLastThirtyDays() {
            // Arrange
            User alice = user("Alice");
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(boardIds);
            when(sprintQueryService.getActiveSprintIdsByBoardIds(boardIds)).thenReturn(List.of());
            Instant from = NOW.minusSeconds(30L * 24 * 3600);
            List<LeaderboardTicketProjection> rows = List.of(row(alice, MissionRank.L, 1));
            when(ticketRepository.findCompletedTicketAssignments(boardIds, from, NOW)).thenReturn(rows);

            // Act
            HomeLeaderboardResponse result = service.getLeaderboard(userId, organizationId, HomeLeaderboardPeriod.SPRINT);

            // Assert
            assertThat(result.period()).isEqualTo(HomeLeaderboardPeriod.LAST_30_DAYS);
            assertThat(result.from()).isEqualTo(from);
            assertThat(result.to()).isEqualTo(NOW);
            assertThat(result.entries()).hasSize(1);
            assertThat(result.entries().get(0).points()).isEqualByComparingTo("5.0");
            verifyNoInteractions(ticketAssigneeRepository);
        }

        @Test
        void lastThirtyDaysPeriod_requested_ignoresActiveSprintAndSplitsByAssigneeCount() {
            // Arrange
            User alice = user("Alice");
            User bob = user("Bob");
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(boardIds);
            Instant from = NOW.minusSeconds(30L * 24 * 3600);
            List<LeaderboardTicketProjection> rows = List.of(
                    row(alice, MissionRank.M, 2), row(bob, MissionRank.M, 2), row(alice, MissionRank.XS, 1));
            when(ticketRepository.findCompletedTicketAssignments(boardIds, from, NOW)).thenReturn(rows);

            // Act
            HomeLeaderboardResponse result = service.getLeaderboard(userId, organizationId, HomeLeaderboardPeriod.LAST_30_DAYS);

            // Assert
            assertThat(result.period()).isEqualTo(HomeLeaderboardPeriod.LAST_30_DAYS);
            assertThat(result.entries()).extracting(HomeLeaderboardEntryResponse::lastName)
                    .containsExactly("Alice", "Bob");
            assertThat(result.entries().get(0).points()).isEqualByComparingTo("2.5");
            assertThat(result.entries().get(0).tickets()).isEqualTo(2);
            assertThat(result.entries().get(1).points()).isEqualByComparingTo("1.5");
            verifyNoInteractions(sprintQueryService);
        }

        @Test
        void userWithoutBoards_returnsLastThirtyDaysWindowWithoutEntries() {
            // Arrange
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of());

            // Act
            HomeLeaderboardResponse result = service.getLeaderboard(userId, organizationId, HomeLeaderboardPeriod.SPRINT);

            // Assert
            assertThat(result.period()).isEqualTo(HomeLeaderboardPeriod.LAST_30_DAYS);
            assertThat(result.from()).isEqualTo(NOW.minusSeconds(30L * 24 * 3600));
            assertThat(result.to()).isEqualTo(NOW);
            assertThat(result.entries()).isEmpty();
            verifyNoInteractions(ticketRepository, ticketAssigneeRepository);
        }
    }

    @Nested
    class FocusBoard {

        private List<UUID> memberIds(Board... boards) {
            List<UUID> ids = new ArrayList<>();
            for (Board board : boards) {
                ids.add(board.getId());
            }
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(ids);
            return ids;
        }

        private void activeSprints(List<UUID> ids, Sprint... sprints) {
            when(sprintQueryService.getActiveSprintsByBoardIds(ids)).thenReturn(List.of(sprints));
        }

        private void mapperReturnsBoards(Board... boards) {
            for (Board board : boards) {
                BoardListResponse response = mock(BoardListResponse.class);
                when(ticketMapper.toHomeBoardResponse(board)).thenReturn(response);
            }
        }

        private void noTickets() {
            when(ticketRepository.findSprintListRoots(any(), any())).thenReturn(List.of());
        }

        @Test
        void noActiveSprint_returnsEmptyShape() {
            // Arrange
            Board kagi = board("Kagi", null);
            List<UUID> ids = memberIds(kagi);
            activeSprints(ids);

            // Act
            HomeSprintResponse result = service.getHomeSprint(userId, organizationId, null);

            // Assert
            assertThat(result.boardId()).isNull();
            assertThat(result.sprintId()).isNull();
            assertThat(result.totalTickets()).isZero();
            assertThat(result.openTickets()).isZero();
            assertThat(result.burndown()).isEmpty();
            assertThat(result.boards()).isEmpty();
            verifyNoInteractions(ticketRepository);
        }

        @Test
        void requestedBoardHasActiveSprint_isTheFocusBoard() {
            // Arrange
            Board root = board("Alpha", null);
            Board child = board("Zulu", root);
            Sprint sprint = sprint("S1", null, null, root, child);
            List<UUID> ids = memberIds(root, child);
            activeSprints(ids, sprint);
            mapperReturnsBoards(root, child);
            noTickets();

            // Act
            HomeSprintResponse result = service.getHomeSprint(userId, organizationId, child.getId());

            // Assert
            assertThat(result.boardId()).isEqualTo(child.getId());
            assertThat(result.boardSlug()).isEqualTo("zulu");
            assertThat(result.sprintId()).isEqualTo(sprint.getId());
            assertThat(result.boards()).hasSize(2);
        }

        @Test
        void noBoardRequested_prefersBoardWithoutParentOverEarlierName() {
            // Arrange
            Board root = board("Zulu", null);
            Board child = board("Alpha", root);
            Sprint sprint = sprint("S1", null, null, root, child);
            List<UUID> ids = memberIds(root, child);
            activeSprints(ids, sprint);
            mapperReturnsBoards(root, child);
            noTickets();

            // Act
            HomeSprintResponse result = service.getHomeSprint(userId, organizationId, null);

            // Assert
            assertThat(result.boardId()).isEqualTo(root.getId());
        }

        @Test
        void everyCandidateHasParent_fallsBackToFirstByNameIgnoringCase() {
            // Arrange
            Board parent = board("Parent", null);
            Board beta = board("beta", parent);
            Board alpha = board("Alpha", parent);
            Sprint sprint = sprint("S1", null, null, beta, alpha);
            List<UUID> ids = memberIds(beta, alpha);
            activeSprints(ids, sprint);
            mapperReturnsBoards(beta, alpha);
            noTickets();

            // Act
            HomeSprintResponse result = service.getHomeSprint(userId, organizationId, null);

            // Assert
            assertThat(result.boardId()).isEqualTo(alpha.getId());
        }

        @Test
        void requestedBoardNotACandidate_fallsBackToDefaultWithoutError() {
            // Arrange
            Board root = board("Root", null);
            Sprint sprint = sprint("S1", null, null, root);
            List<UUID> ids = memberIds(root);
            activeSprints(ids, sprint);
            mapperReturnsBoards(root);
            noTickets();

            // Act
            HomeSprintResponse result = service.getHomeSprint(userId, organizationId, UUID.randomUUID());

            // Assert
            assertThat(result.boardId()).isEqualTo(root.getId());
        }

        @Test
        void sprintSharedWithNonMemberBoard_listsOnlyMemberBoards() {
            // Arrange
            Board mine = board("Mine", null);
            Board foreign = board("Foreign", null);
            Sprint sprint = sprint("S1", null, null, mine, foreign);
            List<UUID> ids = memberIds(mine);
            activeSprints(ids, sprint);
            mapperReturnsBoards(mine);
            noTickets();

            // Act
            HomeSprintResponse result = service.getHomeSprint(userId, organizationId, foreign.getId());

            // Assert
            assertThat(result.boardId()).isEqualTo(mine.getId());
            assertThat(result.boards()).hasSize(1);
            verify(ticketRepository).findSprintListRoots(sprint.getId(), Set.copyOf(ids));
        }

        @Test
        void boardWithTwoActiveSprints_usesTheEarliestStartingOne() {
            // Arrange
            Board root = board("Root", null);
            Sprint later = sprint("Later", Instant.parse("2026-11-10T00:00:00Z"), null, root);
            Sprint undated = sprint("Undated", null, null, root);
            Sprint earlier = sprint("Earlier", Instant.parse("2026-11-01T00:00:00Z"), null, root);
            List<UUID> ids = memberIds(root);
            activeSprints(ids, later, undated, earlier);
            mapperReturnsBoards(root);
            noTickets();

            // Act
            HomeSprintResponse result = service.getHomeSprint(userId, organizationId, null);

            // Assert
            assertThat(result.sprintName()).isEqualTo("Earlier");
            assertThat(result.sprintId()).isEqualTo(earlier.getId());
        }

        @Test
        void scope_countsRootsAndOpenOnesOnly() {
            // Arrange
            Board root = board("Root", null);
            Sprint sprint = sprint("S1", null, null, root);
            List<UUID> ids = memberIds(root);
            activeSprints(ids, sprint);
            mapperReturnsBoards(root);
            List<SprintListRootProjection> scope = List.of(
                    root("DONE", NOW), root("TODO", null), root("IN_PROGRESS", null));
            when(ticketRepository.findSprintListRoots(sprint.getId(), Set.copyOf(ids))).thenReturn(scope);

            // Act
            HomeSprintResponse result = service.getHomeSprint(userId, organizationId, null);

            // Assert
            assertThat(result.totalTickets()).isEqualTo(3);
            assertThat(result.openTickets()).isEqualTo(2);
            assertThat(result.burndown()).isEmpty();
        }
    }

    @Nested
    class Burndown {

        // 2026-11-01T21:00Z is Nov 2 00:00 in Istanbul (UTC+3), the first day of the sprint.
        private final Instant start = Instant.parse("2026-11-01T21:00:00Z");
        private final Instant end = Instant.parse("2026-11-05T21:00:00Z");

        private HomeSprintResponse run(Instant now, Instant sprintStart, Instant sprintEnd,
                                       List<SprintListRootProjection> scope) {
            service = newService(now);
            Board board = board("Root", null);
            Sprint sprint = sprint("S1", sprintStart, sprintEnd, board);
            List<UUID> ids = List.of(board.getId());
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(ids);
            when(sprintQueryService.getActiveSprintsByBoardIds(ids)).thenReturn(List.of(sprint));
            when(ticketMapper.toHomeBoardResponse(board)).thenReturn(mock(BoardListResponse.class));
            when(ticketRepository.findSprintListRoots(eq(sprint.getId()), any())).thenReturn(scope);
            return service.getHomeSprint(userId, organizationId, null);
        }

        private List<SprintListRootProjection> fourTickets() {
            return List.of(
                    root("DONE", Instant.parse("2026-11-02T20:59:59Z")),
                    root("DONE", Instant.parse("2026-11-02T21:00:00Z")),
                    root("DONE", Instant.parse("2026-11-04T05:00:00Z")),
                    root("TODO", null));
        }

        @Test
        void sprintDays_runFromStartToEndDayOnIstanbulCalendar() {
            // Act
            HomeSprintResponse result = run(NOW, start, end, fourTickets());

            // Assert
            assertThat(result.burndown()).extracting(HomeBurndownPointResponse::date).containsExactly(
                    LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4),
                    LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6));
        }

        @Test
        void remaining_finishAtIstanbulMidnight_countsOnTheNewDay() {
            // Act: A is done 23:59:59 on Nov 2, B exactly at Nov 3 00:00 Istanbul
            HomeSprintResponse result = run(NOW, start, end, fourTickets());

            // Assert
            assertThat(result.burndown()).extracting(HomeBurndownPointResponse::remaining)
                    .containsExactly(3, 2, 1, null, null);
        }

        @Test
        void ideal_fallsLinearlyToZeroAtTheEndDay() {
            // Act
            HomeSprintResponse result = run(NOW, start, end, fourTickets());

            // Assert
            assertThat(result.burndown()).extracting(HomeBurndownPointResponse::ideal).usingComparatorForType(
                    BigDecimal::compareTo, BigDecimal.class).containsExactly(
                    new BigDecimal("4.0"), new BigDecimal("3.0"), new BigDecimal("2.0"),
                    new BigDecimal("1.0"), new BigDecimal("0.0"));
        }

        @Test
        void ideal_nonIntegerSlope_roundsHalfUpToOneDecimal() {
            // Arrange
            List<SprintListRootProjection> three = List.of(root("TODO", null), root("TODO", null), root("TODO", null));

            // Act
            HomeSprintResponse result = run(NOW, start, end, three);

            // Assert: 3 * 3/4 = 2.25 -> 2.3, 3 * 1/4 = 0.75 -> 0.8
            assertThat(result.burndown().get(1).ideal()).isEqualByComparingTo("2.3");
            assertThat(result.burndown().get(3).ideal()).isEqualByComparingTo("0.8");
        }

        @Test
        void today_justBeforeIstanbulMidnight_nextDayStaysNull() {
            // Act: 2026-11-04T20:59:59Z is still Nov 4 in Istanbul
            HomeSprintResponse result = run(Instant.parse("2026-11-04T20:59:59Z"), start, end, fourTickets());

            // Assert
            assertThat(result.burndown().get(2).remaining()).isNotNull();
            assertThat(result.burndown().get(3).remaining()).isNull();
        }

        @Test
        void today_atIstanbulMidnight_nextDayGetsAValue() {
            // Act: 2026-11-04T21:00:00Z is Nov 5 00:00 in Istanbul
            HomeSprintResponse result = run(Instant.parse("2026-11-04T21:00:00Z"), start, end, fourTickets());

            // Assert
            assertThat(result.burndown().get(3).remaining()).isEqualTo(1);
            assertThat(result.burndown().get(4).remaining()).isNull();
        }

        @Test
        void sprintInTheFuture_everyActualValueIsNull() {
            // Act
            HomeSprintResponse result = run(Instant.parse("2026-10-01T00:00:00Z"), start, end, fourTickets());

            // Assert
            assertThat(result.burndown()).hasSize(5);
            assertThat(result.burndown()).extracting(HomeBurndownPointResponse::remaining).containsOnlyNulls();
        }

        @Test
        void finishedBeforeSprintStart_isNotOpenOnAnyDay() {
            // Arrange
            List<SprintListRootProjection> scope = List.of(root("DONE", Instant.parse("2026-10-20T00:00:00Z")));

            // Act
            HomeSprintResponse result = run(NOW, start, end, scope);

            // Assert
            assertThat(result.burndown().get(0).remaining()).isZero();
        }

        @Test
        void sprintWithoutDates_hasEmptyBurndownButKeepsCounts() {
            // Act
            HomeSprintResponse result = run(NOW, null, null, fourTickets());

            // Assert
            assertThat(result.burndown()).isEmpty();
            assertThat(result.totalTickets()).isEqualTo(4);
        }

        @Test
        void sprintWithOnlyOneDate_hasEmptyBurndown() {
            // Act
            HomeSprintResponse result = run(NOW, start, null, fourTickets());

            // Assert
            assertThat(result.burndown()).isEmpty();
        }

        @Test
        void endDayBeforeStartDay_hasEmptyBurndown() {
            // Act
            HomeSprintResponse result = run(NOW, end, start, fourTickets());

            // Assert
            assertThat(result.burndown()).isEmpty();
        }

        @Test
        void oneDaySprint_hasSinglePointWithIdealZero() {
            // Act
            HomeSprintResponse result = run(NOW, Instant.parse("2026-11-03T21:00:00Z"),
                    Instant.parse("2026-11-04T10:00:00Z"), fourTickets());

            // Assert
            assertThat(result.burndown()).hasSize(1);
            assertThat(result.burndown().get(0).ideal()).isEqualByComparingTo("0.0");
            assertThat(result.burndown().get(0).remaining()).isEqualTo(1);
        }

        private void assertTwoWeekSprintBurndown(Instant sprintStart, Instant sprintEnd) {
            // Arrange: one ticket done on 30 Oct (Istanbul), one still open
            List<SprintListRootProjection> scope = List.of(
                    root("DONE", Instant.parse("2026-10-30T10:00:00Z")), root("TODO", null));

            // Act
            HomeSprintResponse result = run(NOW, sprintStart, sprintEnd, scope);

            // Assert: 15 points from 27 Oct to 10 Nov; actual values stop after today (4 Nov)
            assertThat(result.burndown()).hasSize(15);
            assertThat(result.burndown()).extracting(HomeBurndownPointResponse::date)
                    .containsExactlyElementsOf(LocalDate.of(2026, 10, 27).datesUntil(LocalDate.of(2026, 11, 11)).toList());
            assertThat(result.burndown()).extracting(HomeBurndownPointResponse::remaining).containsExactly(
                    2, 2, 2, 1, 1, 1, 1, 1, 1, null, null, null, null, null, null);
            assertThat(result.burndown().get(0).ideal()).isEqualByComparingTo("2.0");
            assertThat(result.burndown().get(14).ideal()).isEqualByComparingTo("0.0");
        }

        @Test
        void sprintDays_manualDatesStoredAt21Z_keepFifteenDaysFrom27OctTo10Nov() {
            assertTwoWeekSprintBurndown(Instant.parse("2026-10-26T21:00:00Z"), Instant.parse("2026-11-09T21:00:00Z"));
        }

        @Test
        void sprintDays_automatedDatesStoredAtMidnightUtc_keepFifteenDaysFrom27OctTo10Nov() {
            assertTwoWeekSprintBurndown(Instant.parse("2026-10-27T00:00:00Z"), Instant.parse("2026-11-10T00:00:00Z"));
        }

        @Test
        void emptyScope_zeroTotalsAndZeroIdeal() {
            // Act
            HomeSprintResponse result = run(NOW, start, end, List.of());

            // Assert
            assertThat(result.totalTickets()).isZero();
            assertThat(result.burndown()).extracting(HomeBurndownPointResponse::ideal)
                    .allSatisfy(ideal -> assertThat(ideal).isEqualByComparingTo("0.0"));
        }
    }
}
