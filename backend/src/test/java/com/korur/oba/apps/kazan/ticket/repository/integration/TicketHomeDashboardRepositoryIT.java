package com.korur.oba.apps.kazan.ticket.repository.integration;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 2. Third-party
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.ticket.dto.projection.LeaderboardTicketProjection;
import com.korur.oba.apps.kazan.ticket.dto.projection.SprintListRootProjection;
import com.korur.oba.apps.kazan.ticket.dto.projection.StaleBacklogTicketProjection;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.support.ObaIntegrationTest;

/**
 * Runs the native queries behind the home dashboard (stale backlog, leaderboard window, sprint
 * list roots) and the completed_at backfill of V1_2_89 against PostgreSQL, because the lateral
 * join on comments, the window count of assignees, the half-open date window and the parent
 * left join are SQL that only the real database can check.
 *
 * Each test creates its own rows, asserts only on those, and removes them in the cleanup. The
 * backfill test runs the migration inside a transaction that it rolls back.
 */
class TicketHomeDashboardRepositoryIT extends ObaIntegrationTest {

    private static final String MIGRATION = "db/migration/V1_2_89__Kazan_Add_Ticket_Completed_At.sql";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    /** Every entity and only the ticket repositories, so no controller or service is created. */
    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> ticketIds = new ArrayList<>();
    private final List<UUID> sprintIds = new ArrayList<>();
    private final List<UUID> boardIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    private UUID userId;
    private UUID boardId;
    private UUID backlogId;

    @BeforeEach
    void setUp() {
        userId = insertUser(false);
        boardId = insertBoard(false);
        backlogId = backlogOf(boardId);
    }

    @AfterEach
    void cleanUp() {
        // Comments and assignees go with their ticket (ON DELETE CASCADE).
        for (UUID id : ticketIds) {
            jdbc.update("UPDATE kazan.tickets SET parent_ticket_id = NULL WHERE id = ?", id);
        }
        for (UUID id : ticketIds) {
            jdbc.update("DELETE FROM kazan.tickets WHERE id = ?", id);
        }
        for (UUID id : sprintIds) {
            jdbc.update("DELETE FROM kazan.sprints WHERE id = ?", id);
        }
        for (UUID id : boardIds) {
            jdbc.update("DELETE FROM kazan.boards WHERE id = ?", id);
        }
        for (UUID id : organizationIds) {
            jdbc.update("DELETE FROM shrine.organizations WHERE id = ?", id);
        }
        for (UUID id : userIds) {
            jdbc.update("DELETE FROM shrine.users WHERE id = ?", id);
        }
    }

    private UUID insertUser(boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.users (id, email, first_name, last_name, deleted) VALUES (?, ?, 'Itest', 'User', ?)",
                id, "itest-" + id + "@example.com", deleted);
        userIds.add(id);
        return id;
    }

    private final List<UUID> organizationIds = new ArrayList<>();

    /**
     * Inserts an organization for the boards of one test: every board belongs to one, and
     * deleting the organization in the cleanup removes the boards it still owns.
     */
    private UUID insertOrganization() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.organizations (id, name, status) VALUES (?, ?, 'ACTIVE')",
                id, "itest org " + id);
        organizationIds.add(id);
        return id;
    }

    private UUID insertBoard(boolean deleted) {
        UUID id = UUID.randomUUID();
        String prefix = "I" + id.toString().replace("-", "").substring(0, 8).toUpperCase();
        jdbc.update("INSERT INTO kazan.boards (id, name, prefix, slug, deleted, organization_id) VALUES (?, ?, ?, ?, ?, ?)",
                id, "itest " + prefix, prefix, "itest-" + id, deleted, insertOrganization());
        jdbc.update("INSERT INTO kazan.backlogs (id, board_id) VALUES (?, ?)", UUID.randomUUID(), id);
        boardIds.add(id);
        return id;
    }

    private UUID backlogOf(UUID board) {
        return jdbc.queryForObject("SELECT id FROM kazan.backlogs WHERE board_id = ?", UUID.class, board);
    }

    private UUID insertSprint(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.sprints (id, name, status)
                VALUES (?, 'itest sprint', ?::kazan_sprint_status_enum)
                """, id, status);
        sprintIds.add(id);
        return id;
    }

    /** Inserts a ticket with explicit placement, status, edit time, completion time and parent. */
    private UUID insertTicket(UUID board, UUID sprint, UUID backlog, String status, Instant updatedAt,
                              Instant completedAt, UUID parent, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.tickets (id, title, key, type, status, mission_rank, board_id, created_by_id,
                                            parent_ticket_id, sprint_id, backlog_id, updated_at, completed_at, deleted)
                VALUES (?, 'itest ticket', ?, 'FEATURE'::kazan_ticket_type_enum,
                        ?::kazan_ticket_status_enum, 'M'::kazan_mission_rank_enum, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, "ITEST-" + id, status, board, userId, parent, sprint, backlog,
                Timestamp.from(updatedAt), completedAt == null ? null : Timestamp.from(completedAt), deleted);
        ticketIds.add(id);
        return id;
    }

    private UUID backlogTicket(Instant updatedAt) {
        return insertTicket(boardId, null, backlogId, "TODO", updatedAt, null, null, false);
    }

    private void insertComment(UUID ticket, Instant createdAt, boolean deleted) {
        jdbc.update("""
                INSERT INTO kazan.comments (id, content, ticket_id, author_id, created_at, deleted)
                VALUES (?, 'itest', ?, ?, ?, ?)
                """, UUID.randomUUID(), ticket, userId, Timestamp.from(createdAt), deleted);
    }

    private void assign(UUID ticket, UUID user, boolean deleted) {
        jdbc.update("INSERT INTO kazan.ticket_assignees (id, ticket_id, user_id, deleted) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), ticket, user, deleted);
    }

    private Instant daysAgo(long days) {
        return NOW.minus(days, ChronoUnit.DAYS);
    }

    @Nested
    class StaleBacklog {

        private final Instant cutoff = daysAgo(30);

        private List<UUID> staleIds(List<UUID> boards) {
            return ticketRepository.findStaleBacklogTickets(boards, cutoff, 50).stream()
                    .map(StaleBacklogTicketProjection::getId)
                    .toList();
        }

        @Test
        void findStaleBacklogTickets_oldTicket_isListedWithItsBoardAndActivity() {
            // Arrange
            UUID stale = backlogTicket(daysAgo(40));

            // Act
            List<StaleBacklogTicketProjection> result = ticketRepository.findStaleBacklogTickets(
                    List.of(boardId), cutoff, 50);

            // Assert
            assertThat(result).hasSize(1);
            StaleBacklogTicketProjection row = result.get(0);
            assertThat(row.getId()).isEqualTo(stale);
            assertThat(row.getBoardId()).isEqualTo(boardId);
            assertThat(row.getStatus()).isEqualTo("TODO");
            assertThat(row.getMissionRank()).isEqualTo("M");
            assertThat(row.getLastActivityAt()).isEqualTo(daysAgo(40));
        }

        @Test
        void findStaleBacklogTickets_excludedTickets_areNotListedOrCounted() {
            // Arrange
            UUID stale = backlogTicket(daysAgo(40));
            insertTicket(boardId, insertSprint("ACTIVE"), backlogId, "TODO", daysAgo(40), null, null, false);
            insertTicket(boardId, null, backlogId, "DONE", daysAgo(40), daysAgo(40), null, false);
            insertTicket(boardId, null, backlogId, "TODO", daysAgo(40), null, null, true);
            insertTicket(boardId, null, null, "TODO", daysAgo(40), null, null, false);
            backlogTicket(daysAgo(5));
            UUID recentComment = backlogTicket(daysAgo(40));
            insertComment(recentComment, daysAgo(2), false);
            UUID deletedBoard = insertBoard(true);
            insertTicket(deletedBoard, null, backlogOf(deletedBoard), "TODO", daysAgo(40), null, null, false);
            UUID nonMemberBoard = insertBoard(false);
            insertTicket(nonMemberBoard, null, backlogOf(nonMemberBoard), "TODO", daysAgo(40), null, null, false);

            // Act
            List<UUID> listed = staleIds(List.of(boardId, deletedBoard));
            long count = ticketRepository.countStaleBacklogTickets(List.of(boardId, deletedBoard), cutoff);

            // Assert
            assertThat(listed).containsExactly(stale);
            assertThat(count).isEqualTo(1);
        }

        @Test
        void findStaleBacklogTickets_commentActivity_movesTheLastActivityAndIgnoresDeletedComments() {
            // Arrange
            UUID deletedCommentOnly = backlogTicket(daysAgo(60));
            insertComment(deletedCommentOnly, daysAgo(1), true);
            UUID oldComment = backlogTicket(daysAgo(60));
            insertComment(oldComment, daysAgo(35), false);

            // Act
            List<StaleBacklogTicketProjection> result = ticketRepository.findStaleBacklogTickets(
                    List.of(boardId), cutoff, 50);

            // Assert
            assertThat(result).extracting(StaleBacklogTicketProjection::getId)
                    .containsExactly(deletedCommentOnly, oldComment);
            assertThat(result.get(0).getLastActivityAt()).isEqualTo(daysAgo(60));
            assertThat(result.get(1).getLastActivityAt()).isEqualTo(daysAgo(35));
        }

        @Test
        void findStaleBacklogTickets_manyTickets_ordersOldestFirstAndLimitsRowsButCountsAll() {
            // Arrange
            UUID newest = backlogTicket(daysAgo(31));
            UUID middle = backlogTicket(daysAgo(50));
            UUID oldest = backlogTicket(daysAgo(90));

            // Act
            List<UUID> limited = ticketRepository.findStaleBacklogTickets(List.of(boardId), cutoff, 2).stream()
                    .map(StaleBacklogTicketProjection::getId)
                    .toList();
            long count = ticketRepository.countStaleBacklogTickets(List.of(boardId), cutoff);

            // Assert
            assertThat(limited).containsExactly(oldest, middle);
            assertThat(limited).doesNotContain(newest);
            assertThat(count).isEqualTo(3);
        }

        @Test
        void findStaleBacklogTickets_activityExactlyAtTheCutoff_isNotStale() {
            // Arrange
            UUID atCutoff = backlogTicket(cutoff);
            UUID justBefore = backlogTicket(cutoff.minusMillis(1));

            // Act
            List<UUID> result = staleIds(List.of(boardId));

            // Assert
            assertThat(result).containsExactly(justBefore);
            assertThat(result).doesNotContain(atCutoff);
        }
    }

    @Nested
    class LeaderboardWindow {

        private final Instant from = daysAgo(30);
        private final Instant to = NOW;

        private UUID done(UUID board, Instant completedAt) {
            return insertTicket(board, null, backlogOf(board), "DONE", completedAt, completedAt, null, false);
        }

        private List<UUID> ticketsOf(List<UUID> boards) {
            return ticketRepository.findCompletedTicketAssignments(boards, from, to).stream()
                    .map(LeaderboardTicketProjection::getTicketId)
                    .distinct()
                    .toList();
        }

        @Test
        void findCompletedTicketAssignments_windowBounds_includeFromAndExcludeTo() {
            // Arrange
            UUID atFrom = done(boardId, from);
            UUID inside = done(boardId, daysAgo(10));
            UUID beforeFrom = done(boardId, from.minusMillis(1));
            UUID atTo = done(boardId, to);
            for (UUID ticket : List.of(atFrom, inside, beforeFrom, atTo)) {
                assign(ticket, userId, false);
            }

            // Act
            List<UUID> result = ticketsOf(List.of(boardId));

            // Assert
            assertThat(result).containsExactlyInAnyOrder(atFrom, inside);
        }

        @Test
        void findCompletedTicketAssignments_otherBoardsAndStatuses_areLeftOut() {
            // Arrange
            UUID member = done(boardId, daysAgo(5));
            assign(member, userId, false);
            UUID nonMemberBoard = insertBoard(false);
            assign(done(nonMemberBoard, daysAgo(5)), userId, false);
            UUID reopened = insertTicket(boardId, null, backlogId, "IN_PROGRESS", daysAgo(5), daysAgo(5), null, false);
            assign(reopened, userId, false);
            UUID deletedTicket = insertTicket(boardId, null, backlogId, "DONE", daysAgo(5), daysAgo(5), null, true);
            assign(deletedTicket, userId, false);

            // Act
            List<UUID> result = ticketsOf(List.of(boardId));

            // Assert
            assertThat(result).containsExactly(member);
        }

        @Test
        void findCompletedTicketAssignments_assignees_reportCountAndSkipDeletedAssignmentsAndUsers() {
            // Arrange
            UUID second = insertUser(false);
            UUID deletedUser = insertUser(true);
            UUID shared = done(boardId, daysAgo(3));
            assign(shared, userId, false);
            assign(shared, second, false);
            UUID withGhosts = done(boardId, daysAgo(3));
            assign(withGhosts, userId, false);
            assign(withGhosts, second, true);
            assign(withGhosts, deletedUser, false);
            done(boardId, daysAgo(3));

            // Act
            List<LeaderboardTicketProjection> result = ticketRepository.findCompletedTicketAssignments(
                    List.of(boardId), from, to);

            // Assert
            assertThat(result).filteredOn(row -> row.getTicketId().equals(shared))
                    .extracting(LeaderboardTicketProjection::getUserId)
                    .containsExactlyInAnyOrder(userId, second);
            assertThat(result).filteredOn(row -> row.getTicketId().equals(shared))
                    .allSatisfy(row -> assertThat(row.getAssigneeCount()).isEqualTo(2L));
            assertThat(result).filteredOn(row -> row.getTicketId().equals(withGhosts))
                    .hasSize(1)
                    .allSatisfy(row -> {
                        assertThat(row.getUserId()).isEqualTo(userId);
                        assertThat(row.getAssigneeCount()).isEqualTo(1L);
                        assertThat(row.getMissionRank()).isEqualTo("M");
                    });
            assertThat(result).hasSize(3);
        }
    }

    @Nested
    class SprintListRoots {

        @Test
        void findSprintListRoots_parentRule_dropsOnlyChildrenWhoseLiveParentIsInTheSameSprint() {
            // Arrange
            UUID sprint = insertSprint("ACTIVE");
            UUID otherSprint = insertSprint("PLANNED");
            UUID root = sprintTicket(sprint, "DONE", daysAgo(1), null, false);
            UUID childOfRoot = sprintTicket(sprint, "TODO", null, root, false);
            UUID parentInOtherSprint = sprintTicket(otherSprint, "TODO", null, null, false);
            UUID childOfOtherSprint = sprintTicket(sprint, "TODO", null, parentInOtherSprint, false);
            UUID deletedParent = sprintTicket(sprint, "TODO", null, null, true);
            UUID childOfDeleted = sprintTicket(sprint, "TODO", null, deletedParent, false);
            UUID deletedRoot = sprintTicket(sprint, "TODO", null, null, true);
            sprintTicket(otherSprint, "TODO", null, null, false);
            UUID nonMemberBoard = insertBoard(false);
            insertTicket(nonMemberBoard, sprint, null, "TODO", daysAgo(1), null, null, false);

            // Act
            List<SprintListRootProjection> result = ticketRepository.findSprintListRoots(sprint, List.of(boardId));

            // Assert
            assertThat(result).extracting(SprintListRootProjection::getId)
                    .containsExactlyInAnyOrder(root, childOfOtherSprint, childOfDeleted);
            assertThat(result).extracting(SprintListRootProjection::getId)
                    .doesNotContain(childOfRoot, deletedParent, deletedRoot);
        }

        @Test
        void findSprintListRoots_row_carriesStatusAndCompletionMoment() {
            // Arrange
            UUID sprint = insertSprint("ACTIVE");
            UUID done = sprintTicket(sprint, "DONE", daysAgo(2), null, false);
            UUID open = sprintTicket(sprint, "IN_PROGRESS", null, null, false);

            // Act
            List<SprintListRootProjection> result = ticketRepository.findSprintListRoots(sprint, List.of(boardId));

            // Assert
            assertThat(result).filteredOn(row -> row.getId().equals(done)).singleElement().satisfies(row -> {
                assertThat(row.getStatus()).isEqualTo("DONE");
                assertThat(row.getCompletedAt()).isEqualTo(daysAgo(2));
            });
            assertThat(result).filteredOn(row -> row.getId().equals(open)).singleElement().satisfies(row -> {
                assertThat(row.getStatus()).isEqualTo("IN_PROGRESS");
                assertThat(row.getCompletedAt()).isNull();
            });
        }

        private UUID sprintTicket(UUID sprint, String status, Instant completedAt, UUID parent, boolean deleted) {
            return insertTicket(boardId, sprint, null, status, NOW, completedAt, parent, deleted);
        }
    }

    @Nested
    class CompletedAtBackfill {

        private String migrationSql() {
            try {
                return new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private Instant completedAtOf(UUID id) {
            Timestamp value = jdbc.queryForObject("SELECT completed_at FROM kazan.tickets WHERE id = ?",
                    Timestamp.class, id);
            return value == null ? null : value.toInstant();
        }

        private Instant updatedAtOf(UUID id) {
            return jdbc.queryForObject("SELECT updated_at FROM kazan.tickets WHERE id = ?",
                    Timestamp.class, id).toInstant();
        }

        @Test
        void migration_backfill_setsDoneRowsOnlyFromUpdatedAtAndIsIdempotent() {
            // Arrange
            UUID done = insertTicket(boardId, null, backlogId, "DONE", daysAgo(9), null, null, false);
            UUID doneDeleted = insertTicket(boardId, null, backlogId, "DONE", daysAgo(8), null, null, true);
            UUID doneWithValue = insertTicket(boardId, null, backlogId, "DONE", daysAgo(7), daysAgo(20), null, false);
            UUID todo = insertTicket(boardId, null, backlogId, "TODO", daysAgo(6), null, null, false);
            UUID inReview = insertTicket(boardId, null, backlogId, "IN_REVIEW", daysAgo(5), null, null, false);
            List<Instant> secondRun = new ArrayList<>();

            // Act
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.execute(migrationSql());
                jdbc.execute(migrationSql());
                assertThat(completedAtOf(done)).isEqualTo(daysAgo(9));
                assertThat(completedAtOf(doneDeleted)).isEqualTo(daysAgo(8));
                assertThat(completedAtOf(doneWithValue)).isEqualTo(daysAgo(20));
                assertThat(completedAtOf(todo)).isNull();
                assertThat(completedAtOf(inReview)).isNull();
                assertThat(updatedAtOf(done)).isEqualTo(daysAgo(9));
                secondRun.add(completedAtOf(done));
                status.setRollbackOnly();
            });

            // Assert
            assertThat(secondRun).containsExactly(daysAgo(9));
            assertThat(completedAtOf(done)).isNull();
        }
    }
}
