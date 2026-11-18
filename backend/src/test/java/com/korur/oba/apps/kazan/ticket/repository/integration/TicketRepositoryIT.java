package com.korur.oba.apps.kazan.ticket.repository.integration;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

// 2. Third-party
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.ticket.dto.request.AllTicketQueryRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketQueryRequest;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketPriority;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.apps.kazan.ticket.specification.TicketSpecification;
import com.korur.oba.support.ObaIntegrationTest;

/**
 * Runs the ticket list type filter against the Flyway-migrated
 * PostgreSQL schema, because the PostgreSQL enum type rejects the
 * deprecated TASK value as a bound parameter, which a mock or an in-memory
 * database cannot show.
 *
 * Each test creates its own rows, asserts only on those, and removes them in
 * the cleanup, so a database shared with a running backend is not disturbed.
 */
class TicketRepositoryIT extends ObaIntegrationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> ticketIds = new ArrayList<>();
    private final List<UUID> boardIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();
    private final Map<UUID, UUID> backlogIdByBoardId = new HashMap<>();

    @AfterEach
    void cleanUp() {
        for (UUID id : ticketIds) {
            jdbc.update("DELETE FROM kazan.tickets WHERE id = ?", id);
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

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.users (id, email) VALUES (?, ?)", id, "itest-" + id + "@example.com");
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

    private UUID insertBoard() {
        UUID id = UUID.randomUUID();
        String prefix = "I" + id.toString().replace("-", "").substring(0, 8).toUpperCase();
        jdbc.update("INSERT INTO kazan.boards (id, name, prefix, slug, organization_id) VALUES (?, ?, ?, ?, ?)",
                id, "itest " + prefix, prefix, "itest-" + id, insertOrganization());
        // Every board owns a backlog; the ticket graph loads it with the board.
        UUID backlogId = UUID.randomUUID();
        jdbc.update("INSERT INTO kazan.backlogs (id, board_id) VALUES (?, ?)", backlogId, id);
        backlogIdByBoardId.put(id, backlogId);
        boardIds.add(id);
        return id;
    }

    private UUID insertTicket(UUID boardId, UUID userId, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.tickets (id, title, key, type, mission_rank, board_id, created_by_id)
                VALUES (?, 'itest ticket', ?, ?::kazan_ticket_type_enum, 'M'::kazan_mission_rank_enum, ?, ?)
                """, id, "ITEST-" + id, type, boardId, userId);
        ticketIds.add(id);
        return id;
    }

    private List<UUID> filterIds(UUID boardId, List<TicketType> types) {
        AllTicketQueryRequest request = new AllTicketQueryRequest();
        request.setTypes(types);
        return ticketRepository.findAll(TicketSpecification.filterByBoards(List.of(boardId), request)).stream()
                .map(Ticket::getId)
                .toList();
    }

    @Test
    @SuppressWarnings("deprecation")
    void filterByBoards_onlyTaskType_returnsEmpty() {
        UUID board = insertBoard();
        UUID user = insertUser();
        insertTicket(board, user, "FEATURE");
        insertTicket(board, user, "BUG");

        List<UUID> result = filterIds(board, List.of(TicketType.TASK));

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("deprecation")
    void filterByBoards_taskAndBugTypes_returnsOnlyBugTickets() {
        UUID board = insertBoard();
        UUID user = insertUser();
        insertTicket(board, user, "FEATURE");
        UUID bug = insertTicket(board, user, "BUG");

        List<UUID> result = filterIds(board, List.of(TicketType.TASK, TicketType.BUG));

        assertEquals(List.of(bug), result);
    }

    @Test
    void filterByBoards_problemType_returnsOnlyProblemTickets() {
        UUID board = insertBoard();
        UUID user = insertUser();
        insertTicket(board, user, "BUG");
        UUID problem = insertTicket(board, user, "PROBLEM");

        List<UUID> result = filterIds(board, List.of(TicketType.PROBLEM));

        assertEquals(List.of(problem), result);
    }

    private static final List<TicketPriority> URGENCY_ASCENDING = List.of(
            TicketPriority.LOWEST, TicketPriority.LOW, TicketPriority.MEDIUM, TicketPriority.P3,
            TicketPriority.HIGH, TicketPriority.HIGHEST, TicketPriority.P2, TicketPriority.P1);

    private static List<TicketPriority> urgencyDescending() {
        List<TicketPriority> reversed = new ArrayList<>(URGENCY_ASCENDING);
        Collections.reverse(reversed);
        return reversed;
    }

    /**
     * Inserts one ticket per priority on the board's backlog, in a shuffled
     * order so that insertion order cannot explain a passing sort.
     */
    private void insertTicketPerPriority(UUID boardId, UUID userId) {
        List<TicketPriority> shuffled = new ArrayList<>(URGENCY_ASCENDING);
        Collections.shuffle(shuffled, new Random(32));
        UUID backlogId = backlogIdByBoardId.get(boardId);
        for (TicketPriority priority : shuffled) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO kazan.tickets (id, title, key, type, priority, mission_rank, board_id, backlog_id,
                                                created_by_id)
                    VALUES (?, 'itest ticket', ?, 'FEATURE'::kazan_ticket_type_enum,
                            ?::kazan_ticket_priority_enum, 'M'::kazan_mission_rank_enum, ?, ?, ?)
                    """, id, "ITEST-" + id, priority.name(), boardId, backlogId, userId);
            ticketIds.add(id);
        }
    }

    private List<TicketPriority> priorities(List<Ticket> tickets) {
        return tickets.stream().map(Ticket::getPriority).toList();
    }

    private AllTicketQueryRequest allRequest(String direction) {
        AllTicketQueryRequest request = new AllTicketQueryRequest();
        request.setSortBy("priority");
        request.setSortDirection(direction);
        return request;
    }

    @Test
    void filterByBoards_sortByPriorityDesc_returnsMostUrgentFirst() {
        UUID board = insertBoard();
        insertTicketPerPriority(board, insertUser());
        AllTicketQueryRequest request = allRequest("desc");

        List<Ticket> result = ticketRepository.findAll(
                TicketSpecification.filterByBoards(List.of(board), request), request.toSort());

        assertEquals(urgencyDescending(), priorities(result));
    }

    @Test
    void filterByBoards_sortByPriorityAsc_returnsLeastUrgentFirst() {
        UUID board = insertBoard();
        insertTicketPerPriority(board, insertUser());
        AllTicketQueryRequest request = allRequest("asc");

        List<Ticket> result = ticketRepository.findAll(
                TicketSpecification.filterByBoards(List.of(board), request), request.toSort());

        assertEquals(URGENCY_ASCENDING, priorities(result));
    }

    @Test
    void filterBy_sortByPriorityDesc_returnsMostUrgentFirst() {
        UUID board = insertBoard();
        insertTicketPerPriority(board, insertUser());
        TicketQueryRequest request = new TicketQueryRequest();
        request.setSortBy("priority");
        request.setSortDirection("desc");

        List<Ticket> result = ticketRepository.findAll(
                TicketSpecification.filterBy(List.of(board), List.of(backlogIdByBoardId.get(board)), request),
                request.toSort());

        assertEquals(urgencyDescending(), priorities(result));
    }

    @Test
    void filterByBoards_sortByPriorityDescPaged_keepsOrderAcrossPages() {
        UUID board = insertBoard();
        insertTicketPerPriority(board, insertUser());
        AllTicketQueryRequest request = allRequest("desc");
        Sort sort = request.toSort();

        List<Ticket> first = ticketRepository.findAll(
                TicketSpecification.filterByBoards(List.of(board), request), PageRequest.of(0, 3, sort)).getContent();
        List<Ticket> second = ticketRepository.findAll(
                TicketSpecification.filterByBoards(List.of(board), request), PageRequest.of(1, 3, sort)).getContent();

        assertEquals(List.of(TicketPriority.P1, TicketPriority.P2, TicketPriority.HIGHEST), priorities(first));
        assertEquals(List.of(TicketPriority.HIGH, TicketPriority.P3, TicketPriority.MEDIUM), priorities(second));
    }

    @Test
    void priorityEnum_databaseOrder_matchesJavaDeclarationOrder() {
        List<String> databaseOrder = jdbc.queryForList(
                "SELECT unnest(enum_range(NULL::kazan_ticket_priority_enum))::text", String.class);

        List<String> javaOrder = Arrays.stream(TicketPriority.values()).map(Enum::name).toList();

        assertEquals(javaOrder, databaseOrder);
    }
}
