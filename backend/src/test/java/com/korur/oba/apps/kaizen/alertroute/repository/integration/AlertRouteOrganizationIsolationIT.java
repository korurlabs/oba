package com.korur.oba.apps.kaizen.alertroute.repository.integration;

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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.alertroute.dto.projection.AlertRouteBoardProjection;
import com.korur.oba.apps.kaizen.alertroute.dto.projection.AlertRouteListProjection;
import com.korur.oba.apps.kaizen.alertroute.helper.AlertmanagerIncidentHandler;
import com.korur.oba.apps.kaizen.alertroute.mapper.AlertmanagerIncidentMapper;
import com.korur.oba.apps.kaizen.alertroute.repository.AlertRouteRepository;
import com.korur.oba.apps.kaizen.alertroute.service.impl.AlertRouteQueryServiceImpl;
import com.korur.oba.apps.kaizen.alertroute.validator.AlertRouteValidator;
import com.korur.oba.apps.kaizen.systemactor.entity.SystemActor;
import com.korur.oba.apps.kaizen.systemactor.enums.SystemActorType;
import com.korur.oba.apps.kaizen.systemactor.service.SystemActorQueryService;
import com.korur.oba.apps.kaizen.ticket.dto.command.IncidentReport;
import com.korur.oba.apps.kaizen.ticket.enums.IncidentOutcome;
import com.korur.oba.apps.kaizen.ticket.service.TicketService;
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerAlertRequest;
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerWebhookRequest;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;
import com.korur.oba.support.ObaIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the alert route queries with two organizations in one database, because
 * what they protect is the isolation of the maps: an organization sees, changes,
 * deletes and routes to only its own routes, even when both organizations use the
 * same system key and both have a default. A webhook bound to one organization
 * must file an alert only on that organization's board.
 *
 * Each test creates its own organizations, boards and routes and removes them in
 * the cleanup.
 */
class AlertRouteOrganizationIsolationIT extends ObaIntegrationTest {

    private static final String SYSTEM = "api";

    @Autowired
    private AlertRouteRepository alertRouteRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final UUID orgA = UUID.randomUUID();
    private final UUID orgB = UUID.randomUUID();
    private UUID boardA;
    private UUID boardB;
    private UUID secondBoardA;

    @BeforeEach
    void insertOrganizationsAndBoards() {
        insertOrganization(orgA);
        insertOrganization(orgB);
        boardA = insertBoard(orgA);
        secondBoardA = insertBoard(orgA);
        boardB = insertBoard(orgB);
    }

    @AfterEach
    void cleanUp() {
        // Boards and routes go with the organization (ON DELETE CASCADE).
        jdbc.update("DELETE FROM shrine.organizations WHERE id IN (?, ?)", orgA, orgB);
    }

    private void insertOrganization(UUID id) {
        jdbc.update("INSERT INTO shrine.organizations (id, name, status) VALUES (?, ?, 'ACTIVE')",
                id, "itest org " + id);
    }

    private UUID insertBoard(UUID organizationId) {
        UUID id = UUID.randomUUID();
        String prefix = "I" + id.toString().replace("-", "").substring(0, 8).toUpperCase();
        jdbc.update("INSERT INTO kaizen.boards (id, name, prefix, slug, organization_id) VALUES (?, ?, ?, ?, ?)",
                id, "itest " + prefix, prefix, "itest-" + id, organizationId);
        return id;
    }

    private UUID insertRoute(UUID organizationId, String systemKey, UUID boardId, boolean isDefault) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kaizen.alert_routes (id, system_key, board_id, is_default, organization_id)
                VALUES (?, ?, ?, ?, ?)
                """, id, systemKey, boardId, isDefault, organizationId);
        return id;
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    @Test
    void findAllWithBoard_sameSystemKeyInBothOrganizations_listsOnlyTheOrganizationsOwnRoutes() {
        UUID routeA = insertRoute(orgA, SYSTEM, boardA, true);
        UUID routeB = insertRoute(orgB, SYSTEM, boardB, true);

        List<AlertRouteListProjection> listedA = alertRouteRepository.findAllWithBoard(orgA);
        List<AlertRouteListProjection> listedB = alertRouteRepository.findAllWithBoard(orgB);

        assertEquals(List.of(routeA), listedA.stream().map(AlertRouteListProjection::getId).toList());
        assertEquals(boardA, listedA.get(0).getBoardId());
        assertEquals(List.of(routeB), listedB.stream().map(AlertRouteListProjection::getId).toList());
        assertEquals(boardB, listedB.get(0).getBoardId());
    }

    @Test
    void findWithBoardByIdAndFindBoardRouteById_routeOfAnotherOrganization_isNotFound() {
        UUID routeA = insertRoute(orgA, SYSTEM, boardA, false);

        Optional<AlertRouteListProjection> withBoard = alertRouteRepository.findWithBoardById(orgB, routeA);
        Optional<AlertRouteBoardProjection> boardRoute = alertRouteRepository.findBoardRouteById(orgB, routeA);

        assertTrue(withBoard.isEmpty());
        assertTrue(boardRoute.isEmpty());
        assertTrue(alertRouteRepository.findWithBoardById(orgA, routeA).isPresent());
        assertTrue(alertRouteRepository.findBoardRouteById(orgA, routeA).isPresent());
    }

    @Test
    void updateRouteById_routeOfAnotherOrganization_changesNothing() {
        UUID routeA = insertRoute(orgA, SYSTEM, boardA, false);

        Integer updated = inTransaction(() -> alertRouteRepository.updateRouteById(orgB, routeA, boardB, true));

        assertEquals(0, updated);
        AlertRouteListProjection unchanged = alertRouteRepository.findWithBoardById(orgA, routeA).orElseThrow();
        assertEquals(boardA, unchanged.getBoardId());
        assertFalse(unchanged.getIsDefault());
    }

    @Test
    void updateRouteById_routeOfTheOrganization_repointsToABoardOfTheSameOrganization() {
        UUID routeA = insertRoute(orgA, SYSTEM, boardA, false);

        Integer updated = inTransaction(() -> alertRouteRepository.updateRouteById(orgA, routeA, secondBoardA, false));

        assertEquals(1, updated);
        assertEquals(secondBoardA, alertRouteRepository.findWithBoardById(orgA, routeA).orElseThrow().getBoardId());
    }

    @Test
    void deleteRouteById_routeOfAnotherOrganization_leavesTheRouteInPlace() {
        UUID routeA = insertRoute(orgA, SYSTEM, boardA, false);
        UUID routeB = insertRoute(orgB, SYSTEM, boardB, false);

        Integer deletedWithWrongOrganization = inTransaction(() -> alertRouteRepository.deleteRouteById(orgB, routeA));

        assertEquals(0, deletedWithWrongOrganization);
        assertTrue(alertRouteRepository.findBoardRouteById(orgA, routeA).isPresent());

        Integer deleted = inTransaction(() -> alertRouteRepository.deleteRouteById(orgA, routeA));

        assertEquals(1, deleted);
        assertTrue(alertRouteRepository.findBoardRouteById(orgA, routeA).isEmpty());
        assertTrue(alertRouteRepository.findBoardRouteById(orgB, routeB).isPresent());
    }

    @Test
    void existsByOrganizationIdAndSystemKey_systemMappedOnlyInOneOrganization_isTrueOnlyThere() {
        insertRoute(orgA, "only-a", boardA, false);
        insertRoute(orgA, SYSTEM, boardA, false);
        insertRoute(orgB, SYSTEM, boardB, false);

        assertTrue(alertRouteRepository.existsByOrganizationIdAndSystemKey(orgA, "only-a"));
        assertFalse(alertRouteRepository.existsByOrganizationIdAndSystemKey(orgB, "only-a"));
        assertTrue(alertRouteRepository.existsByOrganizationIdAndSystemKey(orgA, SYSTEM));
        assertTrue(alertRouteRepository.existsByOrganizationIdAndSystemKey(orgB, SYSTEM));
    }

    @Test
    void findBoardIdsBySystemAndFindDefaultBoardId_sameSystemAndDefaultInBothOrganizations_returnEachOwnBoard() {
        insertRoute(orgA, SYSTEM, boardA, false);
        insertRoute(orgA, "web", secondBoardA, true);
        insertRoute(orgB, SYSTEM, boardB, true);

        Map<String, UUID> mapA = boardsBySystem(orgA);
        Map<String, UUID> mapB = boardsBySystem(orgB);

        assertEquals(Map.of(SYSTEM, boardA, "web", secondBoardA), mapA);
        assertEquals(Map.of(SYSTEM, boardB), mapB);
        assertEquals(Optional.of(secondBoardA), alertRouteRepository.findDefaultBoardId(orgA));
        assertEquals(Optional.of(boardB), alertRouteRepository.findDefaultBoardId(orgB));
    }

    @Test
    void findDefaultBoardId_organizationWithoutDefault_isEmptyEvenWhenAnotherOrganizationHasOne() {
        insertRoute(orgA, SYSTEM, boardA, false);
        insertRoute(orgB, SYSTEM, boardB, true);

        assertTrue(alertRouteRepository.findDefaultBoardId(orgA).isEmpty());
    }

    @Test
    void clearDefault_organizationA_leavesTheDefaultOfOrganizationBInPlace() {
        UUID defaultA = insertRoute(orgA, SYSTEM, boardA, true);
        insertRoute(orgB, SYSTEM, boardB, true);

        Integer cleared = inTransaction(() -> alertRouteRepository.clearDefault(orgA));

        assertEquals(1, cleared);
        assertFalse(alertRouteRepository.findWithBoardById(orgA, defaultA).orElseThrow().getIsDefault());
        assertTrue(alertRouteRepository.findDefaultBoardId(orgA).isEmpty());
        assertEquals(Optional.of(boardB), alertRouteRepository.findDefaultBoardId(orgB));
    }

    @Test
    void clearDefault_thenNewDefaultInOrganizationA_keepsOneDefaultPerOrganization() {
        insertRoute(orgA, SYSTEM, boardA, true);
        UUID second = insertRoute(orgA, "web", secondBoardA, false);
        insertRoute(orgB, SYSTEM, boardB, true);

        inTransaction(() -> {
            alertRouteRepository.clearDefault(orgA);
            return alertRouteRepository.updateRouteById(orgA, second, secondBoardA, true);
        });

        assertEquals(Optional.of(secondBoardA), alertRouteRepository.findDefaultBoardId(orgA));
        assertEquals(Optional.of(boardB), alertRouteRepository.findDefaultBoardId(orgB));
    }

    // ---- webhook routing, with the real queries behind the handler ----

    @Test
    void handle_webhookBoundToOrganizationA_filesMappedSystemOnTheBoardOfA() {
        insertRoute(orgA, SYSTEM, boardA, false);
        insertRoute(orgA, "fallback", secondBoardA, true);
        insertRoute(orgB, SYSTEM, boardB, true);
        TicketService ticketService = mock(TicketService.class);
        AlertmanagerIncidentHandler handler = handlerWith(ticketService);

        handler.handle(orgA, request(alert("fp1", SYSTEM)));

        verify(ticketService).reportIncident(any(SystemActor.class), eq(orgA), eq(boardA),
                any(IncidentReport.class), anyBoolean());
        verify(ticketService, never()).reportIncident(any(), any(), eq(boardB), any(), anyBoolean());
    }

    @Test
    void handle_webhookBoundToOrganizationA_sendsUnroutedAlertToTheDefaultOfAAndNeverB() {
        insertRoute(orgA, "fallback", secondBoardA, true);
        insertRoute(orgB, "other", boardB, true);
        TicketService ticketService = mock(TicketService.class);
        AlertmanagerIncidentHandler handler = handlerWith(ticketService);

        handler.handle(orgA, request(alert("fp1", "unmapped")));

        verify(ticketService).reportIncident(any(SystemActor.class), eq(orgA), eq(secondBoardA),
                any(IncidentReport.class), anyBoolean());
        verify(ticketService, never()).reportIncident(any(), any(), eq(boardB), any(), anyBoolean());
    }

    @Test
    void handle_webhookBoundToOrganizationAWithoutRoutesOrDefault_isRefusedEvenWhenBHasBoth() {
        insertRoute(orgB, SYSTEM, boardB, true);
        TicketService ticketService = mock(TicketService.class);
        AlertmanagerIncidentHandler handler = handlerWith(ticketService);

        assertThrows(NotFoundException.class, () -> handler.handle(orgA, request(alert("fp1", SYSTEM))));

        verify(ticketService, never()).reportIncident(any(), any(), any(), any(), anyBoolean());
    }

    private Map<String, UUID> boardsBySystem(UUID organizationId) {
        return new AlertRouteQueryServiceImpl(alertRouteRepository, null).getBoardIdsBySystem(organizationId);
    }

    private AlertmanagerIncidentHandler handlerWith(TicketService ticketService) {
        SystemActorQueryService systemActorQueryService = mock(SystemActorQueryService.class);
        SystemActor actor = SystemActor.builder().name("Alertmanager").type(SystemActorType.ALERTMANAGER).build();
        when(systemActorQueryService.findByType(SystemActorType.ALERTMANAGER)).thenReturn(Optional.of(actor));
        OrganizationQueryService organizationQueryService = mock(OrganizationQueryService.class);
        when(organizationQueryService.getOrganizationEntityById(any(UUID.class)))
                .thenReturn(Organization.builder().status(OrganizationStatus.ACTIVE).build());
        when(ticketService.reportIncident(any(), any(), any(), any(), anyBoolean())).thenReturn(IncidentOutcome.CREATED);
        LoggingService loggingService = mock(LoggingService.class);
        return new AlertmanagerIncidentHandler(systemActorQueryService,
                new AlertRouteQueryServiceImpl(alertRouteRepository, null),
                new AlertRouteValidator(alertRouteRepository, organizationQueryService, loggingService),
                new AlertmanagerIncidentMapper(), ticketService, loggingService);
    }

    private static AlertmanagerAlertRequest alert(String fingerprint, String system) {
        return new AlertmanagerAlertRequest("firing", Map.of("system", system), null, Instant.now(), null, null,
                fingerprint);
    }

    private static AlertmanagerWebhookRequest request(AlertmanagerAlertRequest alert) {
        return new AlertmanagerWebhookRequest("4", null, null, null, null, null, null, null, null, List.of(alert));
    }
}
