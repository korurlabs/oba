package com.korur.oba.apps.kazan.alertroute.helper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.alertroute.constant.service.AlertmanagerIncidentServiceLogConstant;
import com.korur.oba.apps.kazan.alertroute.helper.AlertmanagerIncidentHandler;
import com.korur.oba.apps.kazan.alertroute.mapper.AlertmanagerIncidentMapper;
import com.korur.oba.apps.kazan.alertroute.service.AlertRouteQueryService;
import com.korur.oba.apps.kazan.alertroute.validator.AlertRouteValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.systemactor.entity.SystemActor;
import com.korur.oba.apps.kazan.systemactor.enums.SystemActorType;
import com.korur.oba.apps.kazan.systemactor.service.SystemActorQueryService;
import com.korur.oba.apps.kazan.ticket.dto.command.IncidentReport;
import com.korur.oba.apps.kazan.ticket.enums.IncidentOutcome;
import com.korur.oba.apps.kazan.ticket.service.TicketService;
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerAlertRequest;
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerWebhookRequest;
import com.korur.oba.connect.alertmanager.dto.response.AlertmanagerWebhookResponse;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;

@ExtendWith(MockitoExtension.class)
class AlertmanagerIncidentHandlerUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private TicketService ticketService;

    @Mock
    private LoggingService loggingService;

    @Mock
    private AlertRouteQueryService alertRouteQueryService;

    @Mock
    private SystemActorQueryService systemActorQueryService;

    @Mock
    private AlertRouteValidator alertRouteValidator;

    private AlertmanagerIncidentHandler handler;
    private UUID fabrikamBoard;
    private UUID kagiBoard;
    private UUID defaultBoard;
    private SystemActor actor;

    @BeforeEach
    void setUp() {
        fabrikamBoard = UUID.randomUUID();
        kagiBoard = UUID.randomUUID();
        defaultBoard = UUID.randomUUID();
        actor = SystemActor.builder().name("Alertmanager").type(SystemActorType.ALERTMANAGER).build();
        handler = new AlertmanagerIncidentHandler(systemActorQueryService, alertRouteQueryService,
                alertRouteValidator, new AlertmanagerIncidentMapper(), ticketService, loggingService);
    }

    private void stubRoutes(boolean withDefault) {
        when(systemActorQueryService.findByType(SystemActorType.ALERTMANAGER)).thenReturn(Optional.of(actor));
        when(alertRouteQueryService.getBoardIdsBySystem(organizationId))
                .thenReturn(Map.of("fabrikam", fabrikamBoard, "kagi", kagiBoard));
        when(alertRouteQueryService.getDefaultBoardId(organizationId))
                .thenReturn(withDefault ? Optional.of(defaultBoard) : Optional.empty());
    }

    private static AlertmanagerAlertRequest alert(String status, String fingerprint, String system) {
        Map<String, String> labels = system == null ? null : Map.of("system", system);
        return new AlertmanagerAlertRequest(status, labels, null, Instant.now(), null, null, fingerprint);
    }

    private static AlertmanagerWebhookRequest request(List<AlertmanagerAlertRequest> alerts) {
        return new AlertmanagerWebhookRequest("4", null, null, null, null, null, null, null, null, alerts);
    }

    private static List<AlertmanagerAlertRequest> firingAlerts(int count, String system) {
        List<AlertmanagerAlertRequest> alerts = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            alerts.add(alert("firing", "fp" + index, system));
        }
        return alerts;
    }

    @Test
    void handle_mappedSystem_reportsEachWithActorAndMappedBoard() {
        stubRoutes(true);
        when(ticketService.reportIncident(eq(actor), eq(organizationId), eq(fabrikamBoard), any(IncidentReport.class), eq(true)))
                .thenReturn(IncidentOutcome.CREATED);

        AlertmanagerWebhookResponse response = handler.handle(organizationId, request(firingAlerts(2, "fabrikam")));

        assertThat(response).isEqualTo(new AlertmanagerWebhookResponse(2, 0));
        verify(ticketService).reportIncident(eq(actor), eq(organizationId), eq(fabrikamBoard),
                argThat((IncidentReport report) -> "fp0".equals(report.key())), eq(true));
        verify(ticketService).reportIncident(eq(actor), eq(organizationId), eq(fabrikamBoard),
                argThat((IncidentReport report) -> "fp1".equals(report.key())), eq(true));
        verify(loggingService, never()).warn(any(), any(),
                eq(AlertmanagerIncidentServiceLogConstant.ALERT_SYSTEM_NOT_ROUTED), any(Object[].class));
    }

    @Test
    void handle_systemWithPaddingAndCase_goesToMappedBoard() {
        stubRoutes(true);
        when(ticketService.reportIncident(any(), eq(organizationId), any(), any(), eq(true))).thenReturn(IncidentOutcome.CREATED);

        handler.handle(organizationId, request(List.of(alert("firing", "a", " Fabrikam "))));

        verify(ticketService).reportIncident(eq(actor), eq(organizationId), eq(fabrikamBoard), any(), eq(true));
    }

    @Test
    void handle_unknownSystem_goesToDefaultBoardWithWarn() {
        stubRoutes(true);
        when(ticketService.reportIncident(any(), eq(organizationId), any(), any(), eq(true))).thenReturn(IncidentOutcome.CREATED);

        handler.handle(organizationId, request(List.of(alert("firing", "a", "unknown"))));

        verify(ticketService).reportIncident(eq(actor), eq(organizationId), eq(defaultBoard), any(), eq(true));
        verify(loggingService).warn(any(), eq(LogLayer.SERVICE),
                eq(AlertmanagerIncidentServiceLogConstant.ALERT_SYSTEM_NOT_ROUTED), eq("a"), eq("unknown"));
    }

    @Test
    void handle_missingOrBlankSystem_goesToDefaultBoardLoggedAsNone() {
        stubRoutes(true);
        when(ticketService.reportIncident(any(), eq(organizationId), any(), any(), eq(true))).thenReturn(IncidentOutcome.CREATED);

        handler.handle(organizationId, request(List.of(alert("firing", "a", null), alert("firing", "b", "  "))));

        verify(ticketService, times(2)).reportIncident(eq(actor), eq(organizationId), eq(defaultBoard), any(), eq(true));
        verify(loggingService).warn(any(), eq(LogLayer.SERVICE),
                eq(AlertmanagerIncidentServiceLogConstant.ALERT_SYSTEM_NOT_ROUTED), eq("a"), eq("none"));
        verify(loggingService).warn(any(), eq(LogLayer.SERVICE),
                eq(AlertmanagerIncidentServiceLogConstant.ALERT_SYSTEM_NOT_ROUTED), eq("b"), eq("none"));
    }

    @Test
    void handle_unknownSystemWithoutDefault_throwsBoardNotFoundAndReportsNothing() {
        stubRoutes(false);

        assertThatThrownBy(() -> handler.handle(organizationId, request(firingAlerts(1, "unknown"))))
                .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainKazanError.NOT_BOARD_FOUND.getCode()));

        verifyNoInteractions(ticketService);
    }

    @Test
    void handle_resolvedUnmappedAlertWithoutDefault_isIgnoredNotRefused() {
        stubRoutes(false);

        AlertmanagerWebhookResponse response =
                handler.handle(organizationId, request(List.of(alert("resolved", "a", "unknown"))));

        assertThat(response).isEqualTo(new AlertmanagerWebhookResponse(0, 1));
        verifyNoInteractions(ticketService);
    }

    @Test
    void handle_twoSystems_reportsToTwoBoardsAndReadsMapOnce() {
        stubRoutes(true);
        when(ticketService.reportIncident(any(), eq(organizationId), any(), any(), eq(true))).thenReturn(IncidentOutcome.CREATED);

        handler.handle(organizationId, request(List.of(alert("firing", "a", "fabrikam"), alert("firing", "b", "kagi"))));

        verify(ticketService).reportIncident(eq(actor), eq(organizationId), eq(fabrikamBoard), any(), eq(true));
        verify(ticketService).reportIncident(eq(actor), eq(organizationId), eq(kagiBoard), any(), eq(true));
        verify(alertRouteQueryService, times(1)).getBoardIdsBySystem(organizationId);
        verify(alertRouteQueryService, times(1)).getDefaultBoardId(organizationId);
    }

    @Test
    void handle_mixedOutcomes_countsCreatedAndIgnored() {
        stubRoutes(true);
        when(ticketService.reportIncident(any(), eq(organizationId), any(), any(), eq(true)))
                .thenReturn(IncidentOutcome.CREATED, IncidentOutcome.IGNORED);

        AlertmanagerWebhookResponse response = handler.handle(organizationId, request(List.of(
                alert("firing", "fp1", "kagi"), alert("firing", "fp2", "kagi"), alert("resolved", "fp3", "kagi"))));

        assertThat(response).isEqualTo(new AlertmanagerWebhookResponse(1, 2));
        verify(ticketService, times(2)).reportIncident(any(), eq(organizationId), any(), any(), eq(true));
    }

    @Test
    void handle_twelveAlertsOverTwoBoards_capCountsAcrossBoards() {
        stubRoutes(true);
        when(ticketService.reportIncident(any(), eq(organizationId), any(), any(), eq(true))).thenReturn(IncidentOutcome.CREATED);
        when(ticketService.reportIncident(any(), eq(organizationId), any(), any(), eq(false))).thenReturn(IncidentOutcome.IGNORED);
        List<AlertmanagerAlertRequest> alerts = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            alerts.add(alert("firing", "fp" + index, index % 2 == 0 ? "fabrikam" : "kagi"));
        }

        AlertmanagerWebhookResponse response = handler.handle(organizationId, request(alerts));

        assertThat(response).isEqualTo(new AlertmanagerWebhookResponse(10, 2));
        verify(ticketService, times(10)).reportIncident(any(), eq(organizationId), any(), any(), eq(true));
        verify(ticketService, times(2)).reportIncident(any(), eq(organizationId), any(), any(), eq(false));
        verify(ticketService, times(12)).reportIncident(any(), eq(organizationId), any(), any(), anyBoolean());
        verify(loggingService, times(2)).warn(any(), eq(LogLayer.SERVICE),
                eq(AlertmanagerIncidentServiceLogConstant.ALERT_TICKET_CAP_REACHED), any(), any(), eq(10));
    }

    @Test
    void handle_resolvedAlert_ignoredWithoutReporting() {
        stubRoutes(true);

        AlertmanagerWebhookResponse response =
                handler.handle(organizationId, request(List.of(alert("resolved", "fp1", "fabrikam"))));

        assertThat(response).isEqualTo(new AlertmanagerWebhookResponse(0, 1));
        verifyNoInteractions(ticketService);
    }

    @Test
    void handle_systemActorMissing_throwsUnauthorizedAndWritesNothing() {
        when(systemActorQueryService.findByType(SystemActorType.ALERTMANAGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(organizationId, request(firingAlerts(1, "kagi"))))
                .isInstanceOfSatisfying(UnauthorizedException.class, (UnauthorizedException exception) ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionSecurityError.UNAUTHORIZED.getCode()));

        verify(loggingService).error(any(), eq(LogLayer.SERVICE),
                eq(AlertmanagerIncidentServiceLogConstant.SYSTEM_ACTOR_MISSING), eq(SystemActorType.ALERTMANAGER));
        verifyNoInteractions(alertRouteQueryService, ticketService);
    }

    @Test
    void handle_unknownOrganization_throwsNotFoundAndFilesNothing() {
        doThrow(new NotFoundException(ExceptionDomainOrganizationError.NOT_ORGANIZATION_FOUND))
                .when(alertRouteValidator).ensureOrganizationIsActive(organizationId);

        assertThatThrownBy(() -> handler.handle(organizationId, request(firingAlerts(1, "kagi"))))
                .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                        assertThat(exception.getErrorCode()).isEqualTo("ORG_NOT_001"));

        verifyNoInteractions(alertRouteQueryService, ticketService, systemActorQueryService);
    }

    @Test
    void handle_disabledOrganization_throwsForbiddenAndFilesNothing() {
        doThrow(new ForbiddenException(ExceptionDomainOrganizationError.SEC_ORGANIZATION_DISABLED))
                .when(alertRouteValidator).ensureOrganizationIsActive(organizationId);

        assertThatThrownBy(() -> handler.handle(organizationId, request(firingAlerts(1, "kagi"))))
                .isInstanceOfSatisfying(ForbiddenException.class, (ForbiddenException exception) ->
                        assertThat(exception.getErrorCode()).isEqualTo("ORG_SEC_005"));

        verifyNoInteractions(alertRouteQueryService, ticketService, systemActorQueryService);
    }

    @Test
    void handle_boundOrganization_readsMapAndDefaultOfThatOrganizationOnly() {
        stubRoutes(true);
        when(ticketService.reportIncident(any(), eq(organizationId), any(), any(), eq(true)))
                .thenReturn(IncidentOutcome.CREATED);

        handler.handle(organizationId, request(firingAlerts(1, "unmapped")));

        verify(alertRouteQueryService).getBoardIdsBySystem(organizationId);
        verify(alertRouteQueryService).getDefaultBoardId(organizationId);
        verify(ticketService).reportIncident(eq(actor), eq(organizationId), eq(defaultBoard), any(), eq(true));
    }

    @Test
    void handle_organizationWithoutDefault_unmappedAlertIsNotFiledOnAnotherOrganizationsDefault() {
        when(systemActorQueryService.findByType(SystemActorType.ALERTMANAGER)).thenReturn(Optional.of(actor));
        when(alertRouteQueryService.getBoardIdsBySystem(organizationId)).thenReturn(Map.of("kagi", kagiBoard));
        when(alertRouteQueryService.getDefaultBoardId(organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(organizationId, request(firingAlerts(1, "unmapped"))))
                .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                        assertThat(exception.getErrorCode()).isEqualTo("KAZ_NOT_001"));

        verify(ticketService, never()).reportIncident(any(), any(), any(), any(), anyBoolean());
    }
}
