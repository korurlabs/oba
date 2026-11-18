package com.korur.oba.apps.kazan.alertroute.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.alertroute.dto.response.AlertRouteResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read service interface for an organization's alert map: which board of the
 * organization receives the incidents of each system.
 */
public interface AlertRouteQueryService {

    /**
     * Lists the organization's alert routes, sorted by system key.
     *
     * @param user           the authenticated user
     * @param organizationId the organization that owns the routes
     * @return the organization's alert routes in system key order
     */
    List<AlertRouteResponse> listAlertRoutes(User user, UUID organizationId);

    /**
     * Returns the board id of every mapped system of one organization, keyed by
     * system key. Performs no access check: it serves the Alertmanager webhook,
     * which has no user and whose organization is bound to its token.
     *
     * @param organizationId the organization that owns the routes
     * @return the board id per system key; empty when the organization has no route
     */
    Map<String, UUID> getBoardIdsBySystem(UUID organizationId);

    /**
     * Returns the board id of one organization's default route, which receives the
     * alerts of systems without a route. Performs no access check, as
     * {@link #getBoardIdsBySystem}.
     *
     * @param organizationId the organization that owns the routes
     * @return the default board id, or empty when no route of the organization is the default
     */
    Optional<UUID> getDefaultBoardId(UUID organizationId);
}
