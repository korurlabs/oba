package com.korur.oba.apps.kazan.alertroute.repository.integration;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 2. Third-party
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.alertroute.dto.projection.AlertRouteBoardProjection;
import com.korur.oba.apps.kazan.alertroute.dto.projection.AlertRouteListProjection;
import com.korur.oba.apps.kazan.alertroute.repository.AlertRouteRepository;
import com.korur.oba.support.ObaIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the alert route list, lookup, update and delete queries against the
 * Flyway-migrated PostgreSQL schema, because what they protect is the
 * soft-delete restriction of the board: a soft-deleted board keeps its
 * mappings, so the route must stay listed, repointable and deletable, which a
 * mock cannot show.
 *
 * Each test creates its own boards and routes with unique system keys, asserts
 * only on those, and removes them in the cleanup, so a database shared with a
 * running backend is not disturbed.
 */
class AlertRouteRepositoryIT extends ObaIntegrationTest {

    private final UUID organizationId = UUID.randomUUID();

    @Autowired
    private AlertRouteRepository alertRouteRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> boardIds = new ArrayList<>();

    @BeforeEach
    void insertOrganization() {
        jdbc.update("INSERT INTO shrine.organizations (id, name, status) VALUES (?, ?, 'ACTIVE')",
                organizationId, "itest org " + organizationId);
    }

    @AfterEach
    void cleanUp() {
        for (UUID id : boardIds) {
            jdbc.update("DELETE FROM kazan.alert_routes WHERE board_id = ?", id);
            jdbc.update("DELETE FROM kazan.boards WHERE id = ?", id);
        }
        jdbc.update("DELETE FROM shrine.organizations WHERE id = ?", organizationId);
    }

    private UUID insertBoard() {
        UUID id = UUID.randomUUID();
        String prefix = "I" + id.toString().replace("-", "").substring(0, 8).toUpperCase();
        jdbc.update("INSERT INTO kazan.boards (id, name, prefix, slug, organization_id) VALUES (?, ?, ?, ?, ?)",
                id, "itest " + prefix, prefix, "itest-" + id, organizationId);
        boardIds.add(id);
        return id;
    }

    private void softDeleteBoard(UUID boardId) {
        jdbc.update("UPDATE kazan.boards SET deleted = true WHERE id = ?", boardId);
    }

    private UUID insertRoute(String systemKey, UUID boardId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO kazan.alert_routes (id, system_key, board_id, organization_id) VALUES (?, ?, ?, ?)",
                id, systemKey, boardId, organizationId);
        return id;
    }

    private static String uniqueSystemKey() {
        return "itest-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private Optional<AlertRouteListProjection> listed(UUID routeId) {
        return alertRouteRepository.findAllWithBoard( organizationId).stream()
                .filter((AlertRouteListProjection route) -> route.getId().equals(routeId))
                .findFirst();
    }

    @Test
    void findAllWithBoard_boardSoftDeleted_stillListsRouteWithBoardFields() {
        UUID boardId = insertBoard();
        String systemKey = uniqueSystemKey();
        UUID routeId = insertRoute(systemKey, boardId);
        softDeleteBoard(boardId);

        Optional<AlertRouteListProjection> route = listed(routeId);

        assertTrue(route.isPresent());
        assertEquals(systemKey, route.get().getSystemKey());
        assertEquals(boardId, route.get().getBoardId());
        assertTrue(route.get().getBoardName().startsWith("itest "));
        assertFalse(route.get().getIsDefault());
        assertNotNull(route.get().getUpdatedAt());
    }

    @Test
    void findAllWithBoard_severalRoutes_sortsBySystemKey() {
        UUID boardId = insertBoard();
        String base = uniqueSystemKey();
        insertRoute(base + "-b", boardId);
        insertRoute(base + "-a", boardId);

        List<String> keys = alertRouteRepository.findAllWithBoard( organizationId).stream()
                .map(AlertRouteListProjection::getSystemKey)
                .filter((String key) -> key.startsWith(base))
                .toList();

        assertEquals(List.of(base + "-a", base + "-b"), keys);
    }

    @Test
    void findBoardRouteByIdAndDeleteRouteById_boardSoftDeleted_findsAndDeletesRoute() {
        UUID boardId = insertBoard();
        String systemKey = uniqueSystemKey();
        UUID routeId = insertRoute(systemKey, boardId);
        softDeleteBoard(boardId);

        Optional<AlertRouteBoardProjection> found = alertRouteRepository.findBoardRouteById(organizationId, routeId);
        Integer deleted = new TransactionTemplate(transactionManager)
                .execute(status -> alertRouteRepository.deleteRouteById(organizationId, routeId));

        assertTrue(found.isPresent());
        assertEquals(systemKey, found.get().getSystemKey());
        assertEquals(boardId, found.get().getBoardId());
        assertEquals(1, deleted);
        assertTrue(listed(routeId).isEmpty());
        assertTrue(alertRouteRepository.findBoardRouteById(organizationId, routeId).isEmpty());
    }

    @Test
    void findWithBoardByIdAndUpdateRouteById_boardSoftDeleted_repointsRouteToLiveBoard() {
        UUID deletedBoardId = insertBoard();
        UUID liveBoardId = insertBoard();
        String systemKey = uniqueSystemKey();
        UUID routeId = insertRoute(systemKey, deletedBoardId);
        softDeleteBoard(deletedBoardId);

        Optional<AlertRouteListProjection> before = alertRouteRepository.findWithBoardById(organizationId, routeId);
        // Not the default, so a default row in a shared database is not disturbed.
        Integer updated = new TransactionTemplate(transactionManager)
                .execute(status -> alertRouteRepository.updateRouteById(organizationId, routeId, liveBoardId, false));
        Optional<AlertRouteListProjection> after = alertRouteRepository.findWithBoardById(organizationId, routeId);

        assertTrue(before.isPresent());
        assertEquals(deletedBoardId, before.get().getBoardId());
        assertEquals(1, updated);
        assertTrue(after.isPresent());
        assertEquals(systemKey, after.get().getSystemKey());
        assertEquals(liveBoardId, after.get().getBoardId());
        assertFalse(after.get().getIsDefault());
        assertFalse(after.get().getUpdatedAt().isBefore(before.get().getUpdatedAt()));
    }
}
