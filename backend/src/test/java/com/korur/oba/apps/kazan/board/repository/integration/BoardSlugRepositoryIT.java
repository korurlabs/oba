package com.korur.oba.apps.kazan.board.repository.integration;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.repository.BoardRepository;
import com.korur.oba.apps.kazan.board.repository.BoardSprintRepository;
import com.korur.oba.apps.kazan.sprint.constant.SprintConstant;
import com.korur.oba.support.ObaIntegrationTest;

/**
 * Runs the board and board-sprint slug queries against the Flyway-migrated
 * PostgreSQL schema, because the partial unique indexes on live slugs, the native
 * restore statements and the soft-delete filter cannot be checked on a mock.
 *
 * Each test creates its own rows and removes them in the cleanup.
 */
class BoardSlugRepositoryIT extends ObaIntegrationTest {

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private BoardSprintRepository boardSprintRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> boardIds = new ArrayList<>();
    private final List<UUID> sprintIds = new ArrayList<>();

    /**
     * Runs a modifying repository call in its own transaction, as the services do.
     */
    private <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private void runInTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    @AfterEach
    void cleanUp() {
        for (UUID id : sprintIds) {
            jdbc.update("DELETE FROM kazan.sprints WHERE id = ?", id);
        }
        for (UUID id : boardIds) {
            jdbc.update("DELETE FROM kazan.boards WHERE id = ?", id);
        }
        for (UUID id : organizationIds) {
            jdbc.update("DELETE FROM shrine.organizations WHERE id = ?", id);
        }
    }

    private String unique(String word) {
        return "itest-" + word + "-" + UUID.randomUUID();
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

    private UUID insertBoard(String slug, boolean deleted) {
        UUID id = UUID.randomUUID();
        String prefix = "I" + id.toString().replace("-", "").substring(0, 8).toUpperCase();
        jdbc.update("INSERT INTO kazan.boards (id, name, prefix, slug, deleted, organization_id) VALUES (?, ?, ?, ?, ?, ?)",
                id, "itest " + prefix, prefix, slug, deleted, insertOrganization());
        boardIds.add(id);
        return id;
    }

    private UUID insertSprint() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.sprints (id, name, status)
                VALUES (?, 'itest sprint', 'PLANNED'::kazan_sprint_status_enum)
                """, id);
        sprintIds.add(id);
        return id;
    }

    private UUID insertLink(UUID boardId, UUID sprintId, String slug, boolean deleted, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.board_sprints (id, board_id, sprint_id, slug, deleted, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, boardId, sprintId, slug, deleted, Timestamp.from(createdAt));
        return id;
    }

    private boolean linkDeleted(UUID linkId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT deleted FROM kazan.board_sprints WHERE id = ?", Boolean.class, linkId));
    }

    @Test
    void findBySlug_liveBoard_returnsIt() {
        String slug = unique("live");
        UUID boardId = insertBoard(slug, false);

        Optional<Board> found = boardRepository.findBySlug(slug);

        assertTrue(found.isPresent());
        assertEquals(boardId, found.get().getId());
    }

    @Test
    void findBySlug_deletedBoard_returnsEmpty() {
        String slug = unique("deleted");
        insertBoard(slug, true);

        assertTrue(boardRepository.findBySlug(slug).isEmpty());
        assertFalse(boardRepository.existsBySlug(slug));
    }

    @Test
    void existsBySlugAndIdNot_ownSlug_isFalse() {
        String slug = unique("own");
        UUID boardId = insertBoard(slug, false);

        assertFalse(boardRepository.existsBySlugAndIdNot(slug, boardId));
        assertTrue(boardRepository.existsBySlugAndIdNot(slug, UUID.randomUUID()));
    }

    @Test
    void slugUniqueIndex_secondLiveBoardWithTheSlug_isRefusedButADeletedOneIsNot() {
        String slug = unique("dup");
        insertBoard(slug, true);
        insertBoard(slug, false);

        assertThrows(DuplicateKeyException.class, () -> insertBoard(slug, false));
    }

    @Test
    void updateSlug_deletedBoard_changesItsSlug() {
        UUID boardId = insertBoard(unique("old"), true);
        String newSlug = unique("new");

        runInTransaction(() -> boardRepository.updateSlug(boardId, newSlug));

        assertEquals(newSlug, jdbc.queryForObject("SELECT slug FROM kazan.boards WHERE id = ?", String.class, boardId));
    }

    @Test
    void restoreById_afterUpdateSlugOnATakenSlug_makesTheBoardLiveWithoutViolatingTheIndex() {
        String slug = unique("taken");
        UUID deletedBoard = insertBoard(slug, true);
        insertBoard(slug, false);
        String freeSlug = unique("free");

        runInTransaction(() -> boardRepository.updateSlug(deletedBoard, freeSlug));
        runInTransaction(() -> boardRepository.restoreById(deletedBoard));

        assertTrue(boardRepository.findBySlug(freeSlug).isPresent());
    }

    @Test
    void restoreByIdIfSlugFree_slugFree_restoresTheLink() {
        UUID boardId = insertBoard(unique("board"), false);
        UUID sprintId = insertSprint();
        UUID linkId = insertLink(boardId, sprintId, "planning", true, Instant.now());

        int restored = inTransaction(() -> boardSprintRepository.restoreByIdIfSlugFree(linkId, SprintConstant.RESERVED_SLUGS));

        assertEquals(1, restored);
        assertFalse(linkDeleted(linkId));
    }

    @Test
    void restoreByIdIfSlugFree_slugTakenByLiveLinkOfTheBoard_leavesLinkDeleted() {
        UUID boardId = insertBoard(unique("board"), false);
        insertLink(boardId, insertSprint(), "planning", false, Instant.now());
        UUID deletedLink = insertLink(boardId, insertSprint(), "planning", true, Instant.now());

        int restored = inTransaction(() -> boardSprintRepository.restoreByIdIfSlugFree(deletedLink, SprintConstant.RESERVED_SLUGS));

        assertEquals(0, restored);
        assertTrue(linkDeleted(deletedLink));
    }

    @Test
    void restoreByIdIfSlugFree_slugReserved_leavesLinkDeleted() {
        UUID boardId = insertBoard(unique("board"), false);
        UUID deletedLink = insertLink(boardId, insertSprint(), "backlog", true, Instant.now());

        int restored = inTransaction(() -> boardSprintRepository.restoreByIdIfSlugFree(deletedLink, SprintConstant.RESERVED_SLUGS));

        assertEquals(0, restored);
        assertTrue(linkDeleted(deletedLink));
    }

    @Test
    void restoreByIdIfSlugFree_sameSlugOnAnotherBoard_restoresTheLink() {
        UUID boardId = insertBoard(unique("board"), false);
        UUID otherBoardId = insertBoard(unique("other"), false);
        insertLink(otherBoardId, insertSprint(), "planning", false, Instant.now());
        UUID deletedLink = insertLink(boardId, insertSprint(), "planning", true, Instant.now());

        int restored = inTransaction(() -> boardSprintRepository.restoreByIdIfSlugFree(deletedLink, Set.of("all")));

        assertEquals(1, restored);
    }

    @Test
    void restoreByIdWithSlug_deletedLink_restoresItWithTheNewSlug() {
        UUID boardId = insertBoard(unique("board"), false);
        insertLink(boardId, insertSprint(), "planning", false, Instant.now());
        UUID deletedLink = insertLink(boardId, insertSprint(), "planning", true, Instant.now());

        int restored = inTransaction(() -> boardSprintRepository.restoreByIdWithSlug(deletedLink, "planning-1"));

        assertEquals(1, restored);
        assertEquals("planning-1", jdbc.queryForObject(
                "SELECT slug FROM kazan.board_sprints WHERE id = ?", String.class, deletedLink));
        assertFalse(linkDeleted(deletedLink));
    }

    @Test
    void findDeletedIdsByBoardId_returnsNewestFirstAndOnlyDeletedLinks() {
        UUID boardId = insertBoard(unique("board"), false);
        Instant now = Instant.now();
        UUID oldest = insertLink(boardId, insertSprint(), "a", true, now.minusSeconds(30));
        UUID newest = insertLink(boardId, insertSprint(), "b", true, now.minusSeconds(10));
        insertLink(boardId, insertSprint(), "c", false, now);

        List<UUID> ids = boardSprintRepository.findDeletedIdsByBoardId(boardId);

        assertEquals(List.of(newest, oldest), ids);
    }

    @Test
    void findSprintNameByBoardSprintId_deletedLink_returnsTheSprintName() {
        UUID boardId = insertBoard(unique("board"), false);
        UUID linkId = insertLink(boardId, insertSprint(), "planning", true, Instant.now());

        assertEquals("itest sprint", boardSprintRepository.findSprintNameByBoardSprintId(linkId));
    }

    @Test
    void existsByBoardIdAndSlug_liveLinkOnly_andPerBoard() {
        UUID boardId = insertBoard(unique("board"), false);
        UUID otherBoardId = insertBoard(unique("other"), false);
        UUID linkId = insertLink(boardId, insertSprint(), "planning", false, Instant.now());
        insertLink(boardId, insertSprint(), "gone", true, Instant.now());

        assertTrue(boardSprintRepository.existsByBoard_IdAndSlug(boardId, "planning"));
        assertFalse(boardSprintRepository.existsByBoard_IdAndSlug(otherBoardId, "planning"));
        assertFalse(boardSprintRepository.existsByBoard_IdAndSlug(boardId, "gone"));
        assertFalse(boardSprintRepository.existsByBoard_IdAndSlugAndIdNot(boardId, "planning", linkId));
    }
}
