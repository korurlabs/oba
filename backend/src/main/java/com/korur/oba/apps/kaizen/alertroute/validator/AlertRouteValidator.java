package com.korur.oba.apps.kaizen.alertroute.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.alertroute.constant.AlertRouteConstant;
import com.korur.oba.apps.kaizen.alertroute.constant.AlertRouteExceptionLogConstant;
import com.korur.oba.apps.kaizen.alertroute.dto.projection.AlertRouteBoardProjection;
import com.korur.oba.apps.kaizen.alertroute.dto.projection.AlertRouteListProjection;
import com.korur.oba.apps.kaizen.alertroute.repository.AlertRouteRepository;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;

/**
 * Validator for alert route lookups, the one-route-per-system rule and the
 * organization that Alertmanager notifications are routed in.
 */
@Component
@RequiredArgsConstructor
public class AlertRouteValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AlertRouteValidator.class);

    private final AlertRouteRepository alertRouteRepository;
    private final OrganizationQueryService organizationQueryService;
    private final LoggingService loggingService;

    /**
     * Ensures the alert route exists without loading the entity, and returns it
     * with its board fields. Used by update, so that a route whose board was
     * soft-deleted can still be repointed to a live board. A route of another
     * organization is a missing one.
     *
     * @param organizationId organization that owns the route
     * @param routeId ID of the alert route
     * @return the route with its board fields
     * @throws NotFoundException if the organization has no route with the ID
     */
    public AlertRouteListProjection ensureAlertRouteWithBoardExists(UUID organizationId, UUID routeId) {
        return alertRouteRepository.findWithBoardById(organizationId, routeId)
                .orElseThrow(() -> {
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            AlertRouteExceptionLogConstant.ALERT_ROUTE_NOT_FOUND,
                            routeId);
                    return new NotFoundException(
                            ExceptionDomainKaizenError.NOT_ALERT_ROUTE_FOUND,
                            Map.of(AlertRouteConstant.FIELD_ROUTE_ID, routeId));
                });
    }

    /**
     * Ensures the alert route exists without loading its board, and returns its
     * system key and board id. Used by delete, so that a route whose board was
     * soft-deleted can still be removed. A route of another organization is a
     * missing one.
     *
     * @param organizationId organization that owns the route
     * @param routeId ID of the alert route
     * @return the route's system key and board id
     * @throws NotFoundException if the organization has no route with the ID
     */
    public AlertRouteBoardProjection ensureAlertRouteRowExists(UUID organizationId, UUID routeId) {
        return alertRouteRepository.findBoardRouteById(organizationId, routeId)
                .orElseThrow(() -> {
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            AlertRouteExceptionLogConstant.ALERT_ROUTE_NOT_FOUND,
                            routeId);
                    return new NotFoundException(
                            ExceptionDomainKaizenError.NOT_ALERT_ROUTE_FOUND,
                            Map.of(AlertRouteConstant.FIELD_ROUTE_ID, routeId));
                });
    }

    /**
     * Ensures the system has no alert route yet in the organization: one system
     * maps to one board per organization. The unique constraint on
     * (organization_id, system_key) backs this check against a concurrent create.
     *
     * @param organizationId organization that owns the new route
     * @param systemKey the system key of the new route
     * @throws ConflictException if the organization already has a route for the system
     */
    public void ensureSystemKeyIsUnique(UUID organizationId, String systemKey) {
        if (alertRouteRepository.existsByOrganizationIdAndSystemKey(organizationId, systemKey)) {
            loggingService.error(
                    LOG_PREFIX,
                    LogLayer.VALIDATOR,
                    AlertRouteExceptionLogConstant.ALERT_ROUTE_SYSTEM_DUPLICATE,
                    systemKey);
            throw new ConflictException(
                    ExceptionDomainKaizenError.STA_ALERT_ROUTE_SYSTEM_DUPLICATE,
                    Map.of(AlertRouteConstant.FIELD_SYSTEM_KEY, systemKey));
        }
    }

    /**
     * Ensures the organization bound to the Alertmanager token exists and is ACTIVE.
     * It is checked per notification, not at startup, because the database is not
     * read at startup and an organization can be disabled later. A refused
     * notification is retried by Alertmanager, so the error log is what makes the
     * gap visible.
     *
     * @param organizationId the organization bound to the webhook token
     * @throws NotFoundException if the organization does not exist
     * @throws ForbiddenException {@link ExceptionDomainOrganizationError#SEC_ORGANIZATION_DISABLED}
     *         if the organization is not ACTIVE
     */
    public void ensureOrganizationIsActive(UUID organizationId) {
        Organization organization;
        try {
            organization = organizationQueryService.getOrganizationEntityById(organizationId);
        } catch (NotFoundException exception) {
            loggingService.error(
                    LOG_PREFIX,
                    LogLayer.VALIDATOR,
                    AlertRouteExceptionLogConstant.ORGANIZATION_NOT_FOUND,
                    organizationId);
            throw exception;
        }

        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            loggingService.error(
                    LOG_PREFIX,
                    LogLayer.VALIDATOR,
                    AlertRouteExceptionLogConstant.ORGANIZATION_DISABLED,
                    organizationId,
                    organization.getStatus());
            throw new ForbiddenException(
                    ExceptionDomainOrganizationError.SEC_ORGANIZATION_DISABLED,
                    Map.of(OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId));
        }
    }
}
