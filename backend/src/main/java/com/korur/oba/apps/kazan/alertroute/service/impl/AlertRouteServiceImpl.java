package com.korur.oba.apps.kazan.alertroute.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.kazan.alertroute.constant.service.AlertRouteServiceLogConstant.*;

import com.korur.oba.apps.kazan.alertroute.dto.projection.AlertRouteBoardProjection;
import com.korur.oba.apps.kazan.alertroute.dto.projection.AlertRouteListProjection;
import com.korur.oba.apps.kazan.alertroute.dto.request.AlertRouteCreateRequest;
import com.korur.oba.apps.kazan.alertroute.dto.request.AlertRouteUpdateRequest;
import com.korur.oba.apps.kazan.alertroute.dto.response.AlertRouteResponse;
import com.korur.oba.apps.kazan.alertroute.entity.AlertRoute;
import com.korur.oba.apps.kazan.alertroute.mapper.AlertRouteMapper;
import com.korur.oba.apps.kazan.alertroute.repository.AlertRouteRepository;
import com.korur.oba.apps.kazan.alertroute.service.AlertRouteService;
import com.korur.oba.apps.kazan.alertroute.validator.AlertRouteValidator;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implements writes to an organization's alert map.
 *
 * The organization ADMIN role is checked once per request by the access
 * interceptor. Every route is read and written within the request's
 * organization, so a route of another organization is a missing one. Mapping to
 * a board also needs membership of it, so an admin cannot point alerts at a
 * board they cannot see.
 */
@Service
@RequiredArgsConstructor
public class AlertRouteServiceImpl implements AlertRouteService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AlertRouteServiceImpl.class);

    private final AlertRouteRepository alertRouteRepository;
    private final AlertRouteMapper alertRouteMapper;
    private final AlertRouteValidator alertRouteValidator;
    private final BoardAccessService boardAccessService;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public AlertRouteResponse createAlertRoute(User user, UUID organizationId, AlertRouteCreateRequest request) {
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, request.boardId(), user.getId(), BoardMemberRole.MEMBER);
        alertRouteValidator.ensureSystemKeyIsUnique(organizationId, request.systemKey());

        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        if (isDefault) {
            clearDefault(organizationId);
        }

        AlertRoute alertRoute = alertRouteMapper.toEntity(request);
        alertRoute.setOrganizationId(organizationId);
        alertRoute.setBoard(board);
        alertRoute.setIsDefault(isDefault);
        alertRoute = alertRouteRepository.save(alertRoute);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ALERT_ROUTE_CREATED,
                alertRoute.getId(), alertRoute.getSystemKey(), board.getId(), isDefault);

        return alertRouteMapper.toResponse(alertRoute);
    }

    @Override
    @Transactional
    public AlertRouteResponse updateAlertRoute(User user, UUID organizationId, UUID routeId,
                                               AlertRouteUpdateRequest request) {
        // The route is read and written natively: loading the entity would miss a
        // route whose board was soft-deleted, and repointing such a route to a live
        // board is the repair the admin needs.
        AlertRouteListProjection alertRoute = alertRouteValidator.ensureAlertRouteWithBoardExists(organizationId, routeId);
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, request.boardId(), user.getId(), BoardMemberRole.MEMBER);

        // Clear only when the row becomes the default; a row that already is the
        // default keeps its flag through the update.
        boolean isDefault = request.isDefault();
        if (isDefault && !Boolean.TRUE.equals(alertRoute.getIsDefault())) {
            clearDefault(organizationId);
        }

        alertRouteRepository.updateRouteById(organizationId, routeId, board.getId(), isDefault);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ALERT_ROUTE_UPDATED,
                routeId, alertRoute.getSystemKey(), board.getId(), isDefault);

        return alertRouteMapper.toResponse(alertRouteValidator.ensureAlertRouteWithBoardExists(organizationId, routeId));
    }

    @Override
    @Transactional
    public void deleteAlertRoute(User user, UUID organizationId, UUID routeId) {
        AlertRouteBoardProjection alertRoute = alertRouteValidator.ensureAlertRouteRowExists(organizationId, routeId);

        alertRouteRepository.deleteRouteById(organizationId, routeId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ALERT_ROUTE_DELETED,
                routeId, alertRoute.getSystemKey(), alertRoute.getBoardId());
    }

    /**
     * Removes the default flag from the organization's current default before
     * another row takes it, so the partial unique index never sees two defaults of
     * one organization. A concurrent second default fails on that index and rolls back.
     */
    private void clearDefault(UUID organizationId) {
        alertRouteRepository.clearDefault(organizationId);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ALERT_ROUTE_DEFAULT_CLEARED);
    }
}
