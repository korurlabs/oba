package com.korur.oba.apps.kaizen.alertroute.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.alertroute.constant.AlertIncidentConstant;
import com.korur.oba.apps.kaizen.alertroute.constant.service.AlertmanagerIncidentServiceLogConstant;
import com.korur.oba.apps.kaizen.alertroute.mapper.AlertmanagerIncidentMapper;
import com.korur.oba.apps.kaizen.alertroute.service.AlertRouteQueryService;
import com.korur.oba.apps.kaizen.alertroute.validator.AlertRouteValidator;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.systemactor.entity.SystemActor;
import com.korur.oba.apps.kaizen.systemactor.enums.SystemActorType;
import com.korur.oba.apps.kaizen.systemactor.service.SystemActorQueryService;
import com.korur.oba.apps.kaizen.ticket.dto.command.IncidentReport;
import com.korur.oba.apps.kaizen.ticket.enums.IncidentOutcome;
import com.korur.oba.apps.kaizen.ticket.service.TicketService;
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerAlertRequest;
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerWebhookRequest;
import com.korur.oba.connect.alertmanager.dto.response.AlertmanagerWebhookResponse;
import com.korur.oba.connect.alertmanager.service.AlertmanagerNotificationHandler;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Kaizen's handling of Alertmanager notifications: reports the firing alerts of an Alertmanager notification as incidents, each on
 * the board of the system it comes from. Everything happens in the organization
 * bound to the webhook token: it must exist and be ACTIVE, and only its alert map
 * and boards are used. The board comes from that organization's alert map,
 * selected by the alert's system label, never from a board id in the request, so
 * the token writes only to mapped boards; an unmapped system goes to the
 * organization's default board. The map is read on every notification, so a change applies to the next
 * one. The whole notification is one transaction, so a failure rolls every alert
 * back and Alertmanager's retry delivers the group again; a retry of a committed
 * group finds the open tickets and writes nothing.
 *
 * Each firing alert is reported to the ticket domain, which creates a
 * {@link com.korur.oba.apps.kaizen.ticket.enums.TicketPriority#P1}
 * {@link com.korur.oba.apps.kaizen.ticket.enums.TicketType#INCIDENT} ticket when
 * the alert has no open ticket on that board; a repeated or non-firing alert
 * writes nothing. At most
 * {@link AlertIncidentConstant#MAX_TICKETS_PER_NOTIFICATION} tickets are created
 * across all boards. Without a mapped or default board the whole notification is
 * refused with {@link ExceptionDomainKaizenError#NOT_BOARD_FOUND}. Every ticket
 * names the ALERTMANAGER system actor as its creator; without that actor the
 * notification is refused with 401.
 */
@Component
@RequiredArgsConstructor
public class AlertmanagerIncidentHandler implements AlertmanagerNotificationHandler {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AlertmanagerIncidentHandler.class);

    private final SystemActorQueryService systemActorQueryService;
    private final AlertRouteQueryService alertRouteQueryService;
    private final AlertRouteValidator alertRouteValidator;
    private final AlertmanagerIncidentMapper alertmanagerIncidentMapper;
    private final TicketService ticketService;
    private final LoggingService loggingService;

    /**
     * Files the incident tickets of one notification; see the class for the rules.
     */
    @Override
    @Transactional
    public AlertmanagerWebhookResponse handle(UUID organizationId, AlertmanagerWebhookRequest request) {
        alertRouteValidator.ensureOrganizationIsActive(organizationId);
        SystemActor actor = resolveActor();

        // Read the map once, so every alert of the notification is routed by the same map.
        Map<String, UUID> boardIdsBySystem = alertRouteQueryService.getBoardIdsBySystem(organizationId);
        Optional<UUID> defaultBoardId = alertRouteQueryService.getDefaultBoardId(organizationId);

        int created = 0;
        int ignored = 0;

        for (AlertmanagerAlertRequest alert : request.alerts()) {
            Optional<IncidentReport> report = alertmanagerIncidentMapper.toIncidentReport(request, alert);
            if (report.isEmpty()) {
                loggingService.info(
                        LOG_PREFIX,
                        LogLayer.SERVICE,
                        AlertmanagerIncidentServiceLogConstant.ALERT_NOT_FIRING,
                        alert.fingerprint(),
                        alert.status());
                ignored++;
                continue;
            }

            UUID boardId = resolveBoardId(alert, boardIdsBySystem, defaultBoardId);

            // An alert storm must not flood the boards: past the cap, alerts are only counted.
            boolean ticketCreationAllowed = created < AlertIncidentConstant.MAX_TICKETS_PER_NOTIFICATION;
            IncidentOutcome outcome = ticketService.reportIncident(
                    actor, organizationId, boardId, report.get(), ticketCreationAllowed);

            if (outcome == IncidentOutcome.CREATED) {
                created++;
            } else {
                if (!ticketCreationAllowed) {
                    loggingService.warn(
                            LOG_PREFIX,
                            LogLayer.SERVICE,
                            AlertmanagerIncidentServiceLogConstant.ALERT_TICKET_CAP_REACHED,
                            alert.fingerprint(),
                            boardId,
                            created);
                }
                ignored++;
            }
        }

        loggingService.info(
                LOG_PREFIX,
                LogLayer.SERVICE,
                AlertmanagerIncidentServiceLogConstant.ALERTS_PROCESSED,
                created,
                ignored);

        return new AlertmanagerWebhookResponse(created, ignored);
    }

    /**
     * Finds the ALERTMANAGER system actor that every incident ticket names as its
     * creator. The seed migration creates it; without it no ticket could name a
     * creator, so the notification is refused and Alertmanager retries it.
     */
    private SystemActor resolveActor() {
        Optional<SystemActor> actor = systemActorQueryService.findByType(SystemActorType.ALERTMANAGER);
        if (actor.isEmpty()) {
            loggingService.error(
                    LOG_PREFIX,
                    LogLayer.SERVICE,
                    AlertmanagerIncidentServiceLogConstant.SYSTEM_ACTOR_MISSING,
                    SystemActorType.ALERTMANAGER);
            throw new UnauthorizedException(ExceptionSecurityError.UNAUTHORIZED);
        }
        return actor.get();
    }

    /**
     * Chooses the board of one firing alert: the mapped board of its system label,
     * else the default board. Without either, the alert has nowhere to go, and the
     * whole notification is refused so the gap in the map is visible.
     */
    private UUID resolveBoardId(
            AlertmanagerAlertRequest alert,
            Map<String, UUID> boardIdsBySystem,
            Optional<UUID> defaultBoardId) {
        String system = systemOf(alert);
        if (system != null && boardIdsBySystem.containsKey(system)) {
            return boardIdsBySystem.get(system);
        }

        String loggedSystem = system == null ? AlertIncidentConstant.SYSTEM_NONE : system;
        if (defaultBoardId.isPresent()) {
            loggingService.warn(
                    LOG_PREFIX,
                    LogLayer.SERVICE,
                    AlertmanagerIncidentServiceLogConstant.ALERT_SYSTEM_NOT_ROUTED,
                    alert.fingerprint(),
                    loggedSystem);
            return defaultBoardId.get();
        }

        loggingService.warn(
                LOG_PREFIX,
                LogLayer.SERVICE,
                AlertmanagerIncidentServiceLogConstant.BOARD_NOT_CONFIGURED,
                alert.fingerprint(),
                loggedSystem);
        throw new NotFoundException(ExceptionDomainKaizenError.NOT_BOARD_FOUND);
    }

    /**
     * Reads the system an alert names. Alert rules are written by hand, so a label
     * that differs from a system key only in case or surrounding spaces still
     * reaches that system's board. An alert without a usable label names no
     * system and goes to the default board.
     */
    private static String systemOf(AlertmanagerAlertRequest alert) {
        if (alert.labels() == null) {
            return null;
        }
        String system = alert.labels().get(AlertIncidentConstant.LABEL_SYSTEM);
        if (system == null || system.isBlank()) {
            return null;
        }
        return system.trim().toLowerCase(Locale.ROOT);
    }
}
