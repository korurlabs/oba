package com.korur.oba.shrine.access.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.access.constant.controller.AppGrantControllerLogConstant;
import com.korur.oba.shrine.access.constant.controller.AppGrantControllerResponseConstant;
import com.korur.oba.shrine.access.dto.request.AppGrantCreateRequest;
import com.korur.oba.shrine.access.dto.response.AppGrantResponse;
import com.korur.oba.shrine.access.dto.response.MemberAppAccessResponse;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.access.service.AppAccessService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * REST controller for the app grants of an organization and the effective app access of its members.
 *
 * A grant opens an app to one member or to a group, so only an OWNER or ADMIN of the organization
 * may read or change grants. Each handler states that role with {@link RequiredUserAccess}, and the
 * services make no second organization check.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/organizations/{organizationId}/app-grants")
@Tag(name = "App Grants", description = "Organization app grant and app access APIs")
public class AppGrantController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AppGrantController.class);

    private final AppAccessService appAccessService;
    private final AppAccessQueryService appAccessQueryService;
    private final LoggingService loggingService;

    /**
     * Lists every app grant of an organization, ordered by app, then by target name.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "List app grants")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<AppGrantResponse>>> listGrants(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId) {
        // Log the incoming app grants list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AppGrantControllerLogConstant.APP_GRANTS_LIST_REQUEST,
                organizationId);

        // Query the grants of the organization.
        List<AppGrantResponse> response = appAccessQueryService.listGrants(user, organizationId);

        // Log the completed app grants list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AppGrantControllerLogConstant.APP_GRANTS_LIST_REQUEST_COMPLETED,
                organizationId);

        // Return a 200 OK response with the grants.
        return ResponseUtil.ok(AppGrantControllerResponseConstant.RESPONSE_APP_GRANTS_RETRIEVED, response);
    }

    /**
     * Grants an app to exactly one target: a member or a group. A grant that already exists is
     * returned unchanged.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Grant an app")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Malformed request body or unknown app"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "404", description = "The user is not a member of the organization (ORG_NOT_002), "
                    + "or group not found (APP_NOT_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid request fields, "
                    + "or not exactly one of userId and groupId (APP_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AppGrantResponse>> grantApp(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @RequestBody @Valid AppGrantCreateRequest request) {
        // Log the incoming app grant request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AppGrantControllerLogConstant.APP_GRANT_CREATE_REQUEST,
                organizationId, request.app());

        // Delegate the grant to the service layer.
        AppGrantResponse response = appAccessService.grant(user, organizationId, request);

        // Log the completed app grant operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AppGrantControllerLogConstant.APP_GRANT_CREATE_REQUEST_COMPLETED,
                organizationId, response.id());

        // Return a 201 Created response with the grant.
        return ResponseUtil.created(AppGrantControllerResponseConstant.RESPONSE_APP_GRANTED, response);
    }

    /**
     * Revokes one app grant of an organization.
     */
    @DeleteMapping("/{grantId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Revoke an app grant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "404", description = "Grant not found (APP_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> revokeGrant(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID grantId) {
        // Log the incoming app grant revoke request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AppGrantControllerLogConstant.APP_GRANT_REVOKE_REQUEST,
                organizationId, grantId);

        // Delegate the revoke to the service layer.
        appAccessService.revoke(user, organizationId, grantId);

        // Log the completed app grant revoke operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AppGrantControllerLogConstant.APP_GRANT_REVOKE_REQUEST_COMPLETED,
                organizationId, grantId);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(AppGrantControllerResponseConstant.RESPONSE_APP_GRANT_REVOKED);
    }

    /**
     * Lists the effective apps of each active member of an organization.
     */
    @GetMapping("/app-access")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "List member app access")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<MemberAppAccessResponse>>> listMemberAccess(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId) {
        // Log the incoming member app access list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AppGrantControllerLogConstant.MEMBER_APP_ACCESS_LIST_REQUEST,
                organizationId);

        // Query the effective app access of each member.
        List<MemberAppAccessResponse> response = appAccessQueryService.listMemberAccess(user, organizationId);

        // Log the completed member app access list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AppGrantControllerLogConstant.MEMBER_APP_ACCESS_LIST_REQUEST_COMPLETED,
                organizationId);

        // Return a 200 OK response with the member app access.
        return ResponseUtil.ok(AppGrantControllerResponseConstant.RESPONSE_MEMBER_APP_ACCESS_RETRIEVED, response);
    }
}
