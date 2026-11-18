package com.korur.oba.apps.kazan.alertroute.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.alertroute.dto.request.AlertRouteCreateRequest;
import com.korur.oba.apps.kazan.alertroute.dto.request.AlertRouteUpdateRequest;
import com.korur.oba.apps.kazan.alertroute.dto.response.AlertRouteResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write service interface for an organization's alert map. The caller's
 * {@link com.korur.oba.shrine.organization.enums.OrganizationMemberRole#ADMIN} role
 * in the organization is checked before these methods run; create and update
 * also require membership of the target board, which must belong to the same
 * organization.
 */
public interface AlertRouteService {

    /**
     * Maps a system to a board. When the new route is the default, the previous
     * default of the organization stops being one in the same transaction.
     *
     * @param user           the authenticated user
     * @param organizationId the organization that owns the route
     * @param request        the alert route creation payload
     * @return the created alert route
     */
    AlertRouteResponse createAlertRoute(User user, UUID organizationId, AlertRouteCreateRequest request);

    /**
     * Changes the board and the default flag of an alert route.
     *
     * @param user           the authenticated user
     * @param organizationId the organization that owns the route
     * @param routeId        the alert route identifier
     * @param request        the alert route update payload
     * @return the updated alert route
     */
    AlertRouteResponse updateAlertRoute(User user, UUID organizationId, UUID routeId, AlertRouteUpdateRequest request);

    /**
     * Deletes an alert route; the system's alerts then go to the organization's default board.
     *
     * @param user           the authenticated user
     * @param organizationId the organization that owns the route
     * @param routeId        the alert route identifier
     */
    void deleteAlertRoute(User user, UUID organizationId, UUID routeId);
}
