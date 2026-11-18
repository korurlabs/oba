package com.korur.oba.apps.kaizen.ticket.repository.integration;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 2. Third-party
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.backlog.validator.BacklogValidator;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.repository.BoardRepository;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kaizen.board.service.BoardQueryService;
import com.korur.oba.apps.kaizen.board.validator.BoardValidator;
import com.korur.oba.apps.kaizen.comment.repository.CommentRepository;
import com.korur.oba.apps.kaizen.label.validator.LabelValidator;
import com.korur.oba.apps.kaizen.milestone.repository.MilestoneDependencyRepository;
import com.korur.oba.apps.kaizen.milestone.repository.MilestoneRepository;
import com.korur.oba.apps.kaizen.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kaizen.project.validator.ProjectValidator;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.sprint.service.SprintService;
import com.korur.oba.apps.kaizen.sprint.validator.SprintValidator;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketMoveRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketQueryRequest;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.apps.kaizen.ticket.helper.TicketHelper;
import com.korur.oba.apps.kaizen.ticket.helper.TicketStatusNormalizer;
import com.korur.oba.apps.kaizen.ticket.helper.TicketTypeNormalizer;
import com.korur.oba.apps.kaizen.ticket.mapper.TicketMapper;
import com.korur.oba.apps.kaizen.ticket.repository.TicketAssigneeRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketLabelRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketReviewerRepository;
import com.korur.oba.apps.kaizen.ticket.service.impl.TicketServiceImpl;
import com.korur.oba.apps.kaizen.ticket.specification.TicketSpecification;
import com.korur.oba.apps.kaizen.ticket.validator.TicketValidator;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.support.ObaIntegrationTest;

/**
 * Runs the sprint and backlog list roots, the home-summary sprint and incident counts, the order shift of a
 * move and the sub-ticket placement migration against the Flyway-migrated PostgreSQL
 * schema, because the left joins on the parent, the JPQL count, the bulk order update with the
 * flush and re-read of a move and the recursive migration can only be checked on the real
 * database.
 *
 * Each test creates its own rows, asserts only on those, and removes them in the cleanup, so a
 * database shared with a running backend is not disturbed. The migration test runs the
 * migration inside a transaction that it rolls back, so no other row keeps its effect.
 */
class TicketListRootsRepositoryIT extends ObaIntegrationTest {

    private final UUID organizationId = UUID.randomUUID();

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> ticketIds = new ArrayList<>();
    private final List<UUID> sprintIds = new ArrayList<>();
    private final List<UUID> boardIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();
    private final Map<UUID, UUID> backlogIdByBoardId = new HashMap<>();

    private UUID userId;
    private UUID boardId;
    private UUID backlogId;
    private UUID sprintId;
    private UUID otherSprintId;

    @BeforeEach
    void setUp() {
        userId = insertUser();
        boardId = insertBoard();
        backlogId = backlogIdByBoardId.get(boardId);
        sprintId = insertSprint("ACTIVE");
        otherSprintId = insertSprint("PLANNED");
    }

    @AfterEach
    void cleanUp() {
        // Children reference their parent, so the links go before the rows.
        for (UUID id : ticketIds) {
            jdbc.update("UPDATE kaizen.tickets SET parent_ticket_id = NULL WHERE id = ?", id);
        }
        for (UUID id : ticketIds) {
            jdbc.update("DELETE FROM kaizen.tickets WHERE id = ?", id);
        }
        for (UUID id : sprintIds) {
            jdbc.update("DELETE FROM kaizen.sprints WHERE id = ?", id);
        }
        for (UUID id : boardIds) {
            jdbc.update("DELETE FROM kaizen.boards WHERE id = ?", id);
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
        jdbc.update("INSERT INTO kaizen.boards (id, name, prefix, slug, organization_id) VALUES (?, ?, ?, ?, ?)",
                id, "itest " + prefix, prefix, "itest-" + id, insertOrganization());
        // Every board owns a backlog; the ticket graph loads it with the board.
        UUID backlog = UUID.randomUUID();
        jdbc.update("INSERT INTO kaizen.backlogs (id, board_id) VALUES (?, ?)", backlog, id);
        backlogIdByBoardId.put(id, backlog);
        boardIds.add(id);
        return id;
    }

    private UUID insertSprint(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kaizen.sprints (id, name, status)
                VALUES (?, 'itest sprint', ?::kaizen_sprint_status_enum)
                """, id, status);
        sprintIds.add(id);
        return id;
    }

    private UUID insertMilestone() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kaizen.milestones (id, name, target_date, board_id)
                VALUES (?, 'itest milestone', CURRENT_DATE, ?)
                """, id, boardId);
        return id;
    }

    /**
     * Inserts a ticket on the board with the given placement: a sprint id, else a backlog id,
     * else neither.
     */
    private UUID insertTicket(UUID parentId, UUID sprint, UUID backlog, String status, int displayOrder) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kaizen.tickets (id, title, key, type, status, mission_rank, board_id, created_by_id,
                                            parent_ticket_id, sprint_id, backlog_id, display_order)
                VALUES (?, 'itest ticket', ?, 'FEATURE'::kaizen_ticket_type_enum,
                        ?::kaizen_ticket_status_enum, 'M'::kaizen_mission_rank_enum, ?, ?, ?, ?, ?, ?)
                """, id, "ITEST-" + id, status, boardId, userId, parentId, sprint, backlog, displayOrder);
        ticketIds.add(id);
        return id;
    }

    private UUID inSprint(UUID parentId, UUID sprint) {
        return insertTicket(parentId, sprint, null, "TODO", 0);
    }

    private UUID inBacklog(UUID parentId, UUID backlog) {
        return insertTicket(parentId, null, backlog, "TODO", 0);
    }

    private List<UUID> listIds(List<UUID> backlogIds, TicketQueryRequest request) {
        return ticketRepository.findAll(TicketSpecification.filterBy(List.of(boardId), backlogIds, request)).stream()
                .map(Ticket::getId)
                .toList();
    }

    @Nested
    class SprintListRoots {

        @Test
        void filterBy_sprint_returnsTopLevelAndSubTicketsWhoseParentIsNotInTheSprint() {
            // Arrange
            UUID topLevel = inSprint(null, sprintId);
            UUID parentInSprint = inSprint(null, sprintId);
            UUID subOfParentInSprint = inSprint(parentInSprint, sprintId);
            UUID parentInOtherSprint = inSprint(null, otherSprintId);
            UUID subOfParentInOtherSprint = inSprint(parentInOtherSprint, sprintId);
            UUID parentInBacklog = inBacklog(null, backlogId);
            UUID subOfParentInBacklog = inSprint(parentInBacklog, sprintId);
            TicketQueryRequest request = new TicketQueryRequest();
            request.setSprintId(sprintId);

            // Act
            List<UUID> result = listIds(null, request);

            // Assert
            assertThat(result).containsExactlyInAnyOrder(
                    topLevel, parentInSprint, subOfParentInOtherSprint, subOfParentInBacklog);
            assertThat(result).doesNotContain(subOfParentInSprint, parentInOtherSprint, parentInBacklog);
        }

        @Test
        void filterBy_sprint_nestedTwoLevelsInTheSprint_returnsOnlyTheTopParent() {
            // Arrange
            UUID parent = inSprint(null, sprintId);
            UUID child = inSprint(parent, sprintId);
            inSprint(child, sprintId);
            TicketQueryRequest request = new TicketQueryRequest();
            request.setSprintId(sprintId);

            // Act
            List<UUID> result = listIds(null, request);

            // Assert
            assertThat(result).containsExactly(parent);
        }
    }

    @Nested
    class BacklogListRoots {

        @Test
        void filterBy_backlog_returnsTopLevelAndSubTicketsWhoseParentIsNotInTheBacklog() {
            // Arrange
            UUID topLevel = inBacklog(null, backlogId);
            UUID parentInBacklog = inBacklog(null, backlogId);
            UUID subOfParentInBacklog = inBacklog(parentInBacklog, backlogId);
            UUID parentInSprint = inSprint(null, sprintId);
            UUID subOfParentInSprint = inBacklog(parentInSprint, backlogId);
            UUID parentUnplaced = insertTicket(null, null, null, "TODO", 0);
            UUID subOfParentUnplaced = inBacklog(parentUnplaced, backlogId);
            TicketQueryRequest request = new TicketQueryRequest();
            request.setBacklogId(backlogId);

            // Act
            List<UUID> result = listIds(List.of(backlogId), request);

            // Assert
            assertThat(result).containsExactlyInAnyOrder(
                    topLevel, parentInBacklog, subOfParentInSprint, subOfParentUnplaced);
            assertThat(result).doesNotContain(subOfParentInBacklog);
        }
    }

    @Nested
    class MilestoneList {

        @Test
        void filterBy_milestone_returnsEveryTicketIncludingSubTicketsPlacedWithTheirParent() {
            // Arrange
            UUID milestoneId = insertMilestone();
            UUID parent = inSprint(null, sprintId);
            UUID sub = inSprint(parent, sprintId);
            jdbc.update("UPDATE kaizen.tickets SET milestone_id = ? WHERE id IN (?, ?)", milestoneId, parent, sub);
            TicketQueryRequest request = new TicketQueryRequest();
            request.setMilestoneId(milestoneId);

            // Act
            List<UUID> result = listIds(null, request);

            // Assert
            assertThat(result).containsExactlyInAnyOrder(parent, sub);
        }
    }

    @Nested
    class HomeSummaryCount {

        @Test
        void countSprintListRoots_countsOpenListRootsAndNotTheirParentsOrSubTicketsInTheSprint() {
            // Arrange
            inSprint(null, sprintId);
            UUID parentInSprint = inSprint(null, sprintId);
            inSprint(parentInSprint, sprintId);
            UUID parentInBacklog = inBacklog(null, backlogId);
            inSprint(parentInBacklog, sprintId);
            UUID parentInOtherSprint = inSprint(null, otherSprintId);
            inSprint(parentInOtherSprint, sprintId);
            insertTicket(null, sprintId, null, "DONE", 0);
            insertTicket(parentInBacklog, sprintId, null, "DONE", 0);

            // Act
            long count = ticketRepository.countSprintListRootsByBoardIdsAndSprintIdsAndStatusNot(
                    List.of(boardId), List.of(sprintId), TicketStatus.DONE);

            // Assert: the top-level ticket, the parent in the sprint, and the two sub-tickets
            // planned without their parent.
            assertThat(count).isEqualTo(4);
        }

        @Test
        void countRoots_openIncidents_countsTopLevelOpenIncidentsWithoutParent() {
            // Arrange: two open top-level incidents, one done incident, one open incident that is a
            // sub-ticket, and one open ticket of another type.
            UUID openIncident = inBacklog(null, backlogId);
            UUID inProgressIncident = insertTicket(null, sprintId, null, "IN_PROGRESS", 0);
            UUID doneIncident = insertTicket(null, null, backlogId, "DONE", 0);
            UUID parent = inBacklog(null, backlogId);
            UUID subIncident = inBacklog(parent, backlogId);
            inBacklog(null, backlogId);
            jdbc.update("""
                    UPDATE kaizen.tickets SET type = 'INCIDENT'::kaizen_ticket_type_enum
                    WHERE id IN (?, ?, ?, ?)
                    """, openIncident, inProgressIncident, doneIncident, subIncident);

            // Act
            long count = ticketRepository.countRootsByBoardIdsAndTypeAndStatusNot(
                    List.of(boardId), TicketType.INCIDENT, TicketStatus.DONE);

            // Assert
            assertThat(count).isEqualTo(2);
        }

        @Test
        void countSprintListRoots_doneParentInTheSprint_stillHidesItsOpenSubTicket() {
            // A DONE parent in the sprint is a list root that is not counted, and its open
            // sub-ticket sits under it in the list, so it is not counted either.

            // Arrange
            UUID doneParent = insertTicket(null, sprintId, null, "DONE", 0);
            inSprint(doneParent, sprintId);

            // Act
            long count = ticketRepository.countSprintListRootsByBoardIdsAndSprintIdsAndStatusNot(
                    List.of(boardId), List.of(sprintId), TicketStatus.DONE);

            // Assert
            assertThat(count).isZero();
        }
    }

    @Nested
    class SubTicketPlacementMigration {

        private static final String MIGRATION =
                "db/migration/V1_2_84__Kaizen_Align_Sub_Ticket_Placement.sql";

        private record Placement(UUID sprintId, UUID backlogId, UUID parentId, String status) {
        }

        private Placement placementOf(UUID ticketId) {
            return jdbc.queryForObject(
                    "SELECT sprint_id, backlog_id, parent_ticket_id, status::text FROM kaizen.tickets WHERE id = ?",
                    (rs, rowNum) -> new Placement(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                            rs.getObject(3, UUID.class), rs.getString(4)),
                    ticketId);
        }

        private String migrationSql() {
            try {
                return new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        /**
         * Runs the migration and reads the placements in one transaction, then rolls it back.
         */
        private Map<UUID, Placement> migrateAndRead(List<UUID> ids) {
            Map<UUID, Placement> placements = new HashMap<>();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.execute(migrationSql());
                for (UUID id : ids) {
                    placements.put(id, placementOf(id));
                }
                status.setRollbackOnly();
            });
            return placements;
        }

        @Test
        void migration_staleSubTickets_takeTheirTopLevelAncestorsPlacement() {
            // Arrange
            UUID parentInSprint = inSprint(null, sprintId);
            UUID staleInBacklog = insertTicket(parentInSprint, null, backlogId, "IN_PROGRESS", 0);
            UUID parentInBacklog = inBacklog(null, backlogId);
            UUID staleInSprint = inSprint(parentInBacklog, otherSprintId);
            UUID top = inSprint(null, sprintId);
            UUID middle = inSprint(top, otherSprintId);
            UUID leaf = inBacklog(middle, backlogId);
            UUID unplacedParent = insertTicket(null, null, null, "TODO", 0);
            UUID keepsOwn = inSprint(unplacedParent, otherSprintId);

            // Act
            Map<UUID, Placement> after = migrateAndRead(
                    List.of(staleInBacklog, staleInSprint, middle, leaf, keepsOwn));

            // Assert
            assertThat(after.get(staleInBacklog))
                    .isEqualTo(new Placement(sprintId, null, parentInSprint, "IN_PROGRESS"));
            assertThat(after.get(staleInSprint)).isEqualTo(new Placement(null, backlogId, parentInBacklog, "TODO"));
            assertThat(after.get(middle)).isEqualTo(new Placement(sprintId, null, top, "TODO"));
            assertThat(after.get(leaf)).isEqualTo(new Placement(sprintId, null, middle, "TODO"));
            assertThat(after.get(keepsOwn)).isEqualTo(new Placement(otherSprintId, null, unplacedParent, "TODO"));
            // The rollback leaves the stored rows as they were.
            assertThat(placementOf(staleInBacklog).backlogId()).isEqualTo(backlogId);
        }

        @Test
        void migration_parentCycle_endsAndKeepsEveryParent() {
            // Arrange: X and Y are each other's parent, and Z hangs under X.
            UUID x = inSprint(null, sprintId);
            UUID y = inBacklog(x, backlogId);
            jdbc.update("UPDATE kaizen.tickets SET parent_ticket_id = ? WHERE id = ?", y, x);
            UUID z = inSprint(x, otherSprintId);

            // Act
            Map<UUID, Placement> after = migrateAndRead(List.of(x, y, z));

            // Assert: each ticket takes the placement of the last ticket before the cycle closes.
            assertThat(after.get(x)).isEqualTo(new Placement(null, backlogId, y, "TODO"));
            assertThat(after.get(y)).isEqualTo(new Placement(sprintId, null, x, "TODO"));
            assertThat(after.get(z)).isEqualTo(new Placement(null, backlogId, x, "TODO"));
        }
    }

    @Nested
    class MoveTicketOrder {

        @Test
        void moveTicket_withOrder_responseAndDatabaseShowTheShiftedOrders() {
            // Arrange: a parent at order 5 with an unfinished sub-ticket at order 3, both in the
            // backlog; two other tickets at orders 2 and 4. The parent moves to the sprint at
            // order 2 and takes the sub-ticket along.
            UUID other2 = insertTicket(null, null, backlogId, "TODO", 2);
            UUID sub = insertTicket(null, null, backlogId, "IN_PROGRESS", 3);
            UUID other4 = insertTicket(null, null, backlogId, "TODO", 4);
            UUID parent = insertTicket(null, null, backlogId, "TODO", 5);
            jdbc.update("UPDATE kaizen.tickets SET parent_ticket_id = ? WHERE id = ?", parent, sub);

            TicketMapper ticketMapper = mock(TicketMapper.class);
            Map<UUID, Integer> responseOrders = new HashMap<>();
            when(ticketMapper.toDetailResponse(any(Ticket.class))).thenAnswer(invocation -> {
                Ticket mapped = invocation.getArgument(0);
                responseOrders.put(mapped.getId(), mapped.getDisplayOrder());
                for (Ticket subTicket : mapped.getSubTickets()) {
                    responseOrders.put(subTicket.getId(), subTicket.getDisplayOrder());
                }
                return null;
            });

            // Act
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                User user = entityManager.find(User.class, userId);
                Board board = entityManager.find(Board.class, boardId);
                Sprint sprint = entityManager.find(Sprint.class, sprintId);
                moveService(ticketMapper, user, board, sprint).moveTicket(user, organizationId, boardId, parent,
                        new TicketMoveRequest(sprintId, null, 2, null, null));
            });

            // Assert
            assertThat(responseOrders).containsEntry(parent, 2).containsEntry(sub, 4);
            assertThat(storedOrder(parent)).isEqualTo(2);
            assertThat(storedOrder(other2)).isEqualTo(3);
            assertThat(storedOrder(sub)).isEqualTo(4);
            assertThat(storedOrder(other4)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT sprint_id FROM kaizen.tickets WHERE id = ?", UUID.class, sub))
                    .isEqualTo(sprintId);
            assertThat(jdbc.queryForObject("SELECT parent_ticket_id FROM kaizen.tickets WHERE id = ?",
                    UUID.class, sub)).isEqualTo(parent);
        }

        private Integer storedOrder(UUID ticketId) {
            return jdbc.queryForObject("SELECT display_order FROM kaizen.tickets WHERE id = ?", Integer.class,
                    ticketId);
        }

        /**
         * The service with the real repository, helper, validator and entity manager; access,
         * sprint linking and the mapper are stubbed since the order is under test.
         */
        private TicketServiceImpl moveService(TicketMapper ticketMapper, User user, Board board, Sprint sprint) {
            LoggingService loggingService = new LoggingService();
            TicketHelper ticketHelper = new TicketHelper(
                    ticketRepository,
                    mock(BoardRepository.class),
                    mock(CommentRepository.class),
                    mock(TicketAssigneeRepository.class),
                    mock(TicketReviewerRepository.class),
                    mock(TicketLabelRepository.class),
                    new TicketStatusNormalizer(loggingService),
                    new MilestoneValidator(mock(MilestoneRepository.class), mock(MilestoneDependencyRepository.class),
                            loggingService),
                    loggingService);
            BoardAccessService boardAccessService = mock(BoardAccessService.class);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER))
                    .thenReturn(board);
            SprintValidator sprintValidator = mock(SprintValidator.class);
            when(sprintValidator.ensureSprintExistsInOrganization(organizationId, sprintId)).thenReturn(sprint);
            return new TicketServiceImpl(
                    ticketRepository,
                    mock(TicketAssigneeRepository.class),
                    mock(TicketReviewerRepository.class),
                    mock(TicketLabelRepository.class),
                    mock(CommentRepository.class),
                    mock(BoardRepository.class),
                    boardAccessService,
                    mock(BoardMemberQueryService.class),
                    mock(BoardQueryService.class),
                    mock(BoardValidator.class),
                    mock(AppAccessValidator.class),
                    mock(SprintService.class),
                    sprintValidator,
                    mock(BacklogValidator.class),
                    mock(ProjectValidator.class),
                    new TicketValidator(ticketRepository, loggingService),
                    mock(LabelValidator.class),
                    mock(MilestoneValidator.class),
                    ticketMapper,
                    ticketHelper,
                    new TicketStatusNormalizer(loggingService),
                    new TicketTypeNormalizer(loggingService),
                    mock(UserQueryService.class),
                    loggingService,
                    entityManager,
                    mock(ApplicationEventPublisher.class));
        }
    }
}
