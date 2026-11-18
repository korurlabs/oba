package com.korur.oba.apps.kaizen.alertroute.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.alertroute.constant.controller.AlertRouteControllerLogConstant;
import com.korur.oba.apps.kaizen.alertroute.constant.controller.AlertRouteControllerResponseConstant;
import com.korur.oba.apps.kaizen.alertroute.dto.request.AlertRouteCreateRequest;
import com.korur.oba.apps.kaizen.alertroute.dto.request.AlertRouteUpdateRequest;
import com.korur.oba.apps.kaizen.alertroute.dto.response.AlertRouteResponse;
import com.korur.oba.apps.kaizen.alertroute.service.AlertRouteQueryService;
import com.korur.oba.apps.kaizen.alertroute.service.AlertRouteService;
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * REST controller for an organization's alert map, which names the board that
 * receives the incidents of each system. Only organization admins may read or
 * change it.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaizen/alert-routes")
@Tag(name = "Alert routes", description = "Alert routing from a system to a board")
public class AlertRouteController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AlertRouteController.class);

    private final AlertRouteService alertRouteService;
    private final AlertRouteQueryService alertRouteQueryService;
    private final LoggingService loggingService;

    /**
     * Lists every alert route, sorted by system key.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "List alert routes", description = "Every system-to-board mapping of the organization, sorted by system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_002, "
                    + "ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<AlertRouteResponse>>> listAlertRoutes(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AlertRouteControllerLogConstant.ALERT_ROUTE_LIST_REQUEST);

        List<AlertRouteResponse> response = alertRouteQueryService.listAlertRoutes(user, organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AlertRouteControllerLogConstant.ALERT_ROUTE_LIST_COMPLETED);

        return ResponseUtil.ok(AlertRouteControllerResponseConstant.RESPONSE_ALERT_ROUTE_LIST_RETRIEVED, response);
    }

    /**
     * Maps a system to a board.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Create alert route", description = "Maps a system to a board of the organization; organization admins only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request, or missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_002, "
                    + "ORG_SEC_005), or board role too low (KAI_SEC_002)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization (KAI_NOT_001), "
                    + "or not a member of it (KAI_NOT_002)"),
            @ApiResponse(responseCode = "409", description = "The system already has a board (KAI_STA_012)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AlertRouteResponse>> createAlertRoute(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid AlertRouteCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AlertRouteControllerLogConstant.ALERT_ROUTE_CREATE_REQUEST);

        AlertRouteResponse response = alertRouteService.createAlertRoute(user, organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AlertRouteControllerLogConstant.ALERT_ROUTE_CREATE_COMPLETED);

        return ResponseUtil.created(AlertRouteControllerResponseConstant.RESPONSE_ALERT_ROUTE_CREATED, response);
    }

    /**
     * Changes the board and the default flag of an alert route.
     */
    @PutMapping("/{routeId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Update alert route", description = "Changes the board and the default flag; organization admins only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Invalid request, or missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_002, "
                    + "ORG_SEC_005), or board role too low (KAI_SEC_002)"),
            @ApiResponse(responseCode = "404", description = "Alert route (KAI_NOT_012) or board (KAI_NOT_001) not found "
                    + "or of another organization, or not a member of the board (KAI_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AlertRouteResponse>> updateAlertRoute(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID routeId,
            @RequestBody @Valid AlertRouteUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AlertRouteControllerLogConstant.ALERT_ROUTE_UPDATE_REQUEST);

        AlertRouteResponse response = alertRouteService.updateAlertRoute(user, organizationId, routeId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AlertRouteControllerLogConstant.ALERT_ROUTE_UPDATE_COMPLETED);

        return ResponseUtil.ok(AlertRouteControllerResponseConstant.RESPONSE_ALERT_ROUTE_UPDATED, response);
    }

    /**
     * Deletes an alert route.
     */
    @DeleteMapping("/{routeId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete alert route", description = "Removes a mapping; organization admins only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_002, "
                    + "ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Alert route not found or of another organization (KAI_NOT_012)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteAlertRoute(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID routeId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AlertRouteControllerLogConstant.ALERT_ROUTE_DELETE_REQUEST);

        alertRouteService.deleteAlertRoute(user, organizationId, routeId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AlertRouteControllerLogConstant.ALERT_ROUTE_DELETE_COMPLETED);

        return ResponseUtil.ok(AlertRouteControllerResponseConstant.RESPONSE_ALERT_ROUTE_DELETED);
    }
}
