package com.korur.oba.apps.kazan.sprint.repository.integration;

// 1. Framework-level
import org.hibernate.exception.ConstraintViolationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// 2. Third-party
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.sprint.constant.SprintConstant;
import com.korur.oba.apps.kazan.sprint.constant.SprintRecurrenceConstant;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.entity.SprintSeriesBoard;
import com.korur.oba.apps.kazan.sprint.entity.SprintSeries;
import com.korur.oba.apps.kazan.sprint.enums.SprintStatus;
import com.korur.oba.apps.kazan.sprint.repository.SprintRepository;
import com.korur.oba.apps.kazan.sprint.repository.SprintSeriesRepository;
import com.korur.oba.dojo.util.SlugUtil;
import com.korur.oba.support.ObaIntegrationTest;

/**
 * Runs the sprint series queries against a Flyway-migrated PostgreSQL, because
 * the due rule, the row locks, the unique round per series and the mapping of
 * the entities onto the schema cannot be checked on a mock.
 *
 * Hibernate validates every entity against the schema when the context
 * starts, as the application does, so a mismatch between the entities and
 * the migrations fails this class before any test runs.
 *
 * Each test creates its own rows, asserts only on those, and removes them in
 * the cleanup, so a database shared with a running backend is not disturbed.
 */
class SprintSeriesRepositoryIT extends ObaIntegrationTest {

    @Autowired
    private SprintSeriesRepository sprintSeriesRepository;

    @Autowired
    private SprintRepository sprintRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final List<UUID> seriesIds = new ArrayList<>();
    private final List<UUID> sprintIds = new ArrayList<>();
    private final List<UUID> boardIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID id : sprintIds) {
            jdbc.update("DELETE FROM kazan.board_sprints WHERE sprint_id = ?", id);
        }
        for (UUID id : sprintIds) {
            jdbc.update("DELETE FROM kazan.sprints WHERE id = ?", id);
        }
        for (UUID id : seriesIds) {
            jdbc.update("DELETE FROM kazan.sprint_series WHERE id = ?", id);
        }
        for (UUID id : boardIds) {
            jdbc.update("DELETE FROM kazan.boards WHERE id = ?", id);
        }
        for (UUID id : organizationIds) {
            jdbc.update("DELETE FROM shrine.organizations WHERE id = ?", id);
        }
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

    private UUID insertBoard() {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update("INSERT INTO kazan.boards (id, name, prefix, slug, organization_id) VALUES (?, ?, ?, ?, ?)",
                id, "itest board " + suffix, "I" + suffix, "itest-" + suffix, insertOrganization());
        // A board is mapped with its backlog, so the entity cannot be loaded without one.
        jdbc.update("INSERT INTO kazan.backlogs (id, board_id) VALUES (?, ?)", UUID.randomUUID(), id);
        boardIds.add(id);
        return id;
    }

    private void linkSprintToBoard(UUID sprintId, UUID boardId) {
        jdbc.update("INSERT INTO kazan.board_sprints (id, board_id, sprint_id, slug) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), boardId, sprintId, "itest-" + sprintId.toString().substring(0, 8));
    }

    private UUID insertSeries(boolean enabled, Instant nextCreationAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.sprint_series (id, name, next_round, enabled, length_days, next_creation_at)
                VALUES (?, 'itest series', 2, ?, 7, ?)
                """, id, enabled, nextCreationAt == null ? null : Timestamp.from(nextCreationAt));
        seriesIds.add(id);
        return id;
    }

    private UUID insertSprint(UUID seriesId, int round, String status, Instant start, Instant end) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.sprints (id, name, status, start_date, end_date, series_id, round)
                VALUES (?, 'itest sprint', ?::kazan_sprint_status_enum, ?, ?, ?, ?)
                """, id, status, Timestamp.from(start), Timestamp.from(end), seriesId, round);
        sprintIds.add(id);
        return id;
    }

    @Test
    void findDueIds_activeSprintEnded_returnsSeries() {
        Instant now = Instant.now();
        UUID series = insertSeries(true, null);
        insertSprint(series, 1, "ACTIVE", now.minus(7, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS));

        assertTrue(sprintSeriesRepository.findDueIds(now).contains(series));
    }

    @Test
    void findDueIds_creationTimePassed_returnsSeries() {
        Instant now = Instant.now();
        UUID series = insertSeries(true, now.minus(1, ChronoUnit.MINUTES));
        insertSprint(series, 1, "ACTIVE", now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));

        assertTrue(sprintSeriesRepository.findDueIds(now).contains(series));
    }

    @Test
    void findDueIds_onlyPlannedSprintPastItsStart_returnsSeries() {
        Instant now = Instant.now();
        UUID series = insertSeries(true, null);
        insertSprint(series, 1, "PLANNED", now.minus(1, ChronoUnit.HOURS), now.plus(7, ChronoUnit.DAYS));

        assertTrue(sprintSeriesRepository.findDueIds(now).contains(series));
    }

    @Test
    void findDueIds_runningSprintOrSwitchedOff_skipsSeries() {
        Instant now = Instant.now();
        UUID running = insertSeries(true, now.plus(1, ChronoUnit.DAYS));
        insertSprint(running, 1, "ACTIVE", now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        UUID off = insertSeries(false, now.minus(1, ChronoUnit.DAYS));
        insertSprint(off, 1, "ACTIVE", now.minus(7, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS));

        List<UUID> due = sprintSeriesRepository.findDueIds(now);

        assertFalse(due.contains(running));
        assertFalse(due.contains(off));
    }

    @Test
    void sameRoundTwice_isRefusedByTheDatabase() {
        Instant now = Instant.now();
        UUID series = insertSeries(true, null);
        insertSprint(series, 2, "PLANNED", now, now.plus(7, ChronoUnit.DAYS));

        assertThrows(DataIntegrityViolationException.class,
                () -> insertSprint(series, 2, "PLANNED", now, now.plus(7, ChronoUnit.DAYS)));
    }

    @Test
    void findByIdForUpdateSkipLocked_rowHeldElsewhere_returnsEmpty() throws Exception {
        UUID series = insertSeries(true, null);
        TransactionTemplate holder = new TransactionTemplate(transactionManager);
        holder.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionTemplate runner = new TransactionTemplate(transactionManager);
        runner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> holding = CompletableFuture.runAsync(() -> holder.executeWithoutResult(status -> {
            sprintSeriesRepository.findByIdForUpdate(series);
            locked.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }));
        try {
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            Optional<SprintSeries> skipped = runner.execute(status ->
                    sprintSeriesRepository.findByIdForUpdateSkipLocked(series));
            assertTrue(skipped != null && skipped.isEmpty());
        } finally {
            release.countDown();
            holding.get(10, TimeUnit.SECONDS);
        }

        Optional<SprintSeries> free = runner.execute(status ->
                sprintSeriesRepository.findByIdForUpdateSkipLocked(series));
        assertTrue(free != null && free.isPresent());
    }

    @Test
    void sameRoundAsSoftDeletedSprint_isRefusedByTheDatabase() {
        Instant now = Instant.now();
        UUID series = insertSeries(true, null);
        UUID first = insertSprint(series, 2, "PLANNED", now, now.plus(7, ChronoUnit.DAYS));
        jdbc.update("UPDATE kazan.sprints SET deleted = TRUE WHERE id = ?", first);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertSprint(series, 2, "PLANNED", now, now.plus(7, ChronoUnit.DAYS)));
    }

    @Test
    void sameRoundInAnotherSeries_isAccepted() {
        Instant now = Instant.now();
        UUID one = insertSeries(true, null);
        UUID other = insertSeries(true, null);
        insertSprint(one, 2, "PLANNED", now, now.plus(7, ChronoUnit.DAYS));

        insertSprint(other, 2, "PLANNED", now, now.plus(7, ChronoUnit.DAYS));
    }

    @Test
    void roundWithoutSeries_isRefusedByTheDatabase() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO kazan.sprints (id, name, status, round)
                VALUES (?, 'itest sprint', 'PLANNED'::kazan_sprint_status_enum, 1)
                """, UUID.randomUUID()));
    }

    @Test
    void findDueIds_softDeletedActiveSprint_isIgnored() {
        Instant now = Instant.now();
        UUID series = insertSeries(true, null);
        UUID ended = insertSprint(series, 1, "ACTIVE", now.minus(7, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS));
        jdbc.update("UPDATE kazan.sprints SET deleted = TRUE WHERE id = ?", ended);

        assertFalse(sprintSeriesRepository.findDueIds(now).contains(series));
    }

    @Test
    void findByIdForUpdate_rowHeldElsewhere_waitsForTheHolder() throws Exception {
        UUID series = insertSeries(true, null);
        TransactionTemplate holder = new TransactionTemplate(transactionManager);
        holder.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionTemplate waiter = new TransactionTemplate(transactionManager);
        waiter.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Each side reports the pid of its database backend once it is in its transaction.
        CompletableFuture<Integer> holderBackend = new CompletableFuture<>();
        CompletableFuture<Integer> waiterBackend = new CompletableFuture<>();
        CountDownLatch release = new CountDownLatch(1);
        // Threads of the test's own, so neither side queues behind other work in the common pool.
        ExecutorService threads = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<Void> holding = CompletableFuture.runAsync(() -> holder.executeWithoutResult(status -> {
                int pid = backendPid();
                if (sprintSeriesRepository.findByIdForUpdate(series).isEmpty()) {
                    holderBackend.completeExceptionally(new IllegalStateException("holder found no series to lock"));
                    return;
                }
                holderBackend.complete(pid);
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }), threads);
            CompletableFuture<Optional<SprintSeries>> waiting;
            try {
                int holderPid = holderBackend.get(10, TimeUnit.SECONDS);
                waiting = CompletableFuture.supplyAsync(() -> waiter.execute(status -> {
                    waiterBackend.complete(backendPid());
                    return sprintSeriesRepository.findByIdForUpdate(series);
                }), threads);
                int waiterPid = waiterBackend.get(10, TimeUnit.SECONDS);

                // The database itself reports the waiter's backend as blocked by the holder's.
                assertTrue(awaitBlockedBy(waiterPid, holderPid),
                        () -> "backend " + waiterPid + " was not reported blocked by backend " + holderPid
                                + "; waiter done: " + waiting.isDone() + "; waiter activity: " + activityOf(waiterPid));
                assertFalse(waiting.isDone());
            } finally {
                release.countDown();
                holding.get(10, TimeUnit.SECONDS);
            }

            Optional<SprintSeries> result = waiting.get(10, TimeUnit.SECONDS);
            assertTrue(result.isPresent());
        } finally {
            release.countDown();
            threads.shutdownNow();
        }
    }

    /**
     * The pid of the database backend of the current transaction, read through the entity
     * manager, so it is the connection the repository's lock query runs on.
     */
    private int backendPid() {
        return ((Number) entityManager.createNativeQuery("SELECT pg_backend_pid()").getSingleResult()).intValue();
    }

    /**
     * Polls until the waiter's backend waits on a lock the holder's backend holds. Both are
     * named by pid, so neither the SQL text nor other sessions on the server affect the result.
     */
    private boolean awaitBlockedBy(int waiterPid, int holderPid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Boolean blocked = jdbc.queryForObject("SELECT ? = ANY (pg_blocking_pids(?))",
                    Boolean.class, holderPid, waiterPid);
            if (Boolean.TRUE.equals(blocked)) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
        }
        return false;
    }

    /** What the database reports for a backend, for a failure message. */
    private String activityOf(int pid) {
        return jdbc.queryForList(
                "SELECT state, wait_event_type, wait_event, query FROM pg_stat_activity WHERE pid = ?", pid).toString();
    }

    @Test
    void findIdsByBoardIdsAndStatus_completedSprintNotYetSaved_isFlushedFirstAndNotReportedActive() {
        Instant now = Instant.now();
        UUID board = insertBoard();
        UUID series = insertSeries(true, null);
        UUID closing = insertSprint(series, 1, "ACTIVE", now.minus(7, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS));
        linkSprintToBoard(closing, board);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        List<UUID> active = transaction.execute(status -> {
            Sprint sprint = sprintRepository.findById(closing).orElseThrow();
            // Only the managed entity changes: nothing is saved or flushed by hand.
            sprint.setStatus(SprintStatus.COMPLETED);
            return sprintRepository.findIdsByBoardIdsAndStatus(List.of(board), SprintStatus.ACTIVE);
        });

        assertTrue(active != null && active.isEmpty());
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT status::text FROM kazan.sprints WHERE id = ?", String.class, closing));
    }

    @Test
    void findIdsByBoardIdsAndStatus_otherActiveSprintOnTheBoard_isReported() {
        Instant now = Instant.now();
        UUID board = insertBoard();
        UUID series = insertSeries(true, null);
        UUID closing = insertSprint(series, 1, "ACTIVE", now.minus(7, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS));
        linkSprintToBoard(closing, board);

        List<UUID> active = sprintRepository.findIdsByBoardIdsAndStatus(List.of(board), SprintStatus.ACTIVE);

        assertEquals(List.of(closing), active);
    }

    // ---------- one series per board ----------

    private void insertSeriesBoard(UUID seriesId, UUID boardId, int position) {
        jdbc.update("INSERT INTO kazan.sprint_series_boards (id, series_id, board_id, position) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), seriesId, boardId, position);
    }

    private List<UUID> boardsOfSeries(UUID seriesId) {
        return jdbc.queryForList("SELECT board_id FROM kazan.sprint_series_boards WHERE series_id = ?",
                UUID.class, seriesId);
    }

    @Test
    void secondRowForTheSameBoardInAnotherSeries_isRefusedByTheUniqueConstraint() {
        UUID board = insertBoard();
        UUID first = insertSeries(true, null);
        UUID second = insertSeries(false, null);
        insertSeriesBoard(first, board, 0);

        DataIntegrityViolationException refused = assertThrows(DataIntegrityViolationException.class,
                () -> insertSeriesBoard(second, board, 0));

        assertTrue(refused.getMessage().contains(SprintRecurrenceConstant.SERIES_BOARD_UNIQUE_INDEX));
        assertEquals(List.of(board), boardsOfSeries(first));
        assertTrue(boardsOfSeries(second).isEmpty());
    }

    @Test
    void secondRowSavedThroughTheSeries_isRecognisedAsALostRaceByItsConstraintName() {
        UUID board = insertBoard();
        UUID first = insertSeries(true, null);
        UUID second = insertSeries(true, null);
        insertSeriesBoard(first, board, 0);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        DataIntegrityViolationException refused = assertThrows(DataIntegrityViolationException.class,
                () -> transaction.executeWithoutResult(status -> {
                    SprintSeries series = sprintSeriesRepository.findById(second).orElseThrow();
                    series.getBoards().add(SprintSeriesBoard.builder()
                            .series(series).board(entityManager.find(Board.class, board)).position(0).build());
                    sprintSeriesRepository.saveAndFlush(series);
                }));

        assertTrue(SlugUtil.isSlugConflict(refused, SprintRecurrenceConstant.SERIES_BOARD_UNIQUE_INDEX));
        assertFalse(SlugUtil.isSlugConflict(refused, SprintConstant.SLUG_UNIQUE_INDEX));
    }

    @Test
    void movingARowFromADisabledSeriesToAnother_withOneFlushPerSeries_succeeds() {
        UUID board = insertBoard();
        UUID other = insertBoard();
        UUID disabled = insertSeries(false, null);
        UUID target = insertSeries(true, null);
        insertSeriesBoard(disabled, board, 0);
        insertSeriesBoard(disabled, other, 1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            SprintSeries from = sprintSeriesRepository.findByIdForUpdate(disabled).orElseThrow();
            from.getBoards().removeIf(row -> row.getBoard().getId().equals(board));
            sprintSeriesRepository.saveAndFlush(from);
            SprintSeries to = sprintSeriesRepository.findByIdForUpdate(target).orElseThrow();
            to.getBoards().add(SprintSeriesBoard.builder()
                    .series(to).board(entityManager.find(Board.class, board)).position(0).build());
            sprintSeriesRepository.saveAndFlush(to);
        });

        assertEquals(List.of(board), boardsOfSeries(target));
        assertEquals(List.of(other), boardsOfSeries(disabled));
    }

    @Test
    void movingARowFromADisabledSeriesToAnother_inOneFlush_isRefusedBecauseTheInsertRunsBeforeTheOrphanDelete() {
        UUID board = insertBoard();
        UUID disabled = insertSeries(false, null);
        UUID target = insertSeries(true, null);
        insertSeriesBoard(disabled, board, 0);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        ConstraintViolationException refused = assertThrows(ConstraintViolationException.class,
                () -> transaction.executeWithoutResult(status -> {
            SprintSeries from = sprintSeriesRepository.findById(disabled).orElseThrow();
            from.getBoards().removeIf(row -> row.getBoard().getId().equals(board));
            SprintSeries to = sprintSeriesRepository.findById(target).orElseThrow();
            to.getBoards().add(SprintSeriesBoard.builder()
                    .series(to).board(entityManager.find(Board.class, board)).position(0).build());
            // The raw entity manager flush is not translated by Spring.
            entityManager.flush();
        }));

        assertEquals(SprintRecurrenceConstant.SERIES_BOARD_UNIQUE_INDEX, refused.getConstraintName());

        assertEquals(List.of(board), boardsOfSeries(disabled));
        assertTrue(boardsOfSeries(target).isEmpty());
    }

    @Test
    void sameSlugOnTheSameBoardTwice_isRefusedByTheSlugIndexButNotWhenTheFirstLinkIsSoftDeleted() {
        Instant now = Instant.now();
        UUID board = insertBoard();
        UUID series = insertSeries(true, null);
        UUID first = insertSprint(series, 1, "PLANNED", now, now.plus(7, ChronoUnit.DAYS));
        UUID second = insertSprint(series, 2, "PLANNED", now, now.plus(7, ChronoUnit.DAYS));
        jdbc.update("INSERT INTO kazan.board_sprints (id, board_id, sprint_id, slug) VALUES (?, ?, ?, 'taken')",
                UUID.randomUUID(), board, first);

        DataIntegrityViolationException refused = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO kazan.board_sprints (id, board_id, sprint_id, slug) VALUES (?, ?, ?, 'taken')",
                        UUID.randomUUID(), board, second));
        assertTrue(refused.getMessage().contains(SprintConstant.SLUG_UNIQUE_INDEX));

        jdbc.update("UPDATE kazan.board_sprints SET deleted = TRUE WHERE sprint_id = ?", first);
        jdbc.update("INSERT INTO kazan.board_sprints (id, board_id, sprint_id, slug) VALUES (?, ?, ?, 'taken')",
                UUID.randomUUID(), board, second);
    }
}
