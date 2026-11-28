package com.korur.oba.connect.azure.controller;

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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.constant.AzureConnectionConstant;
import com.korur.oba.connect.azure.constant.controller.AzureConnectionControllerLogConstant;
import com.korur.oba.connect.azure.constant.controller.AzureConnectionControllerResponseConstant;
import com.korur.oba.connect.azure.dto.request.AzureConnectionRequest;
import com.korur.oba.connect.azure.dto.response.AzureConnectionResponse;
import com.korur.oba.connect.azure.service.AzureConnectionCheckService;
import com.korur.oba.connect.azure.service.AzureConnectionQueryService;
import com.korur.oba.connect.azure.service.AzureConnectionService;
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
 * REST controller for an organization's Azure connections. A connection belongs to the
 * organization and not to an app, so its paths sit under no app, and only the organization's
 * owners and admins may read or change it, the roles that manage the organization itself.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(AzureConnectionConstant.CONNECTIONS_PATH)
@Tag(name = "Azure connections", description = "The organization's links to Azure through workload identity "
        + "federation, without a secret")
public class AzureConnectionController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AzureConnectionController.class);

    private final AzureConnectionService connectionService;
    private final AzureConnectionQueryService connectionQueryService;
    private final AzureConnectionCheckService connectionCheckService;
    private final LoggingService loggingService;

    /**
     * Lists the organization's connections in name order.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "List Azure connections", description = "Every connection of the organization in name "
            + "order, with its subscriptions and setup values; organization admins only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<AzureConnectionResponse>>> listConnections(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AzureConnectionControllerLogConstant.CONNECTION_LIST_REQUEST);

        List<AzureConnectionResponse> response = connectionQueryService.listConnections(organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AzureConnectionControllerLogConstant.CONNECTION_LIST_COMPLETED);

        return ResponseUtil.ok(AzureConnectionControllerResponseConstant.RESPONSE_CONNECTION_LIST_RETRIEVED, response);
    }

    /**
     * Returns one connection of the organization.
     */
    @GetMapping(AzureConnectionConstant.CONNECTION_PATH)
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Get Azure connection", description = "One connection with its subscriptions and setup "
            + "values; organization admins only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Connection not found or of another organization "
                    + "(AZR_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AzureConnectionResponse>> getConnection(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID connectionId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AzureConnectionControllerLogConstant.CONNECTION_GET_REQUEST);

        AzureConnectionResponse response = connectionQueryService.getConnection(organizationId, connectionId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AzureConnectionControllerLogConstant.CONNECTION_GET_COMPLETED);

        return ResponseUtil.ok(AzureConnectionControllerResponseConstant.RESPONSE_CONNECTION_RETRIEVED, response);
    }

    /**
     * Creates a PENDING connection and returns the values its federated credential must name.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Create Azure connection", description = "Adds a PENDING connection whose subject is "
            + "fixed at create; organization admins only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header, or a "
                    + "body that is not valid JSON or holds an id that is not a UUID"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "No OpenID Connect issuer is configured on this "
                    + "deployment (AZR_NOT_002)"),
            @ApiResponse(responseCode = "409", description = "The organization already has a connection with this "
                    + "name (AZR_STA_004) or for this tenant and client id (AZR_STA_003)"),
            @ApiResponse(responseCode = "422", description = "Invalid request (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AzureConnectionResponse>> createConnection(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid AzureConnectionRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AzureConnectionControllerLogConstant.CONNECTION_CREATE_REQUEST);

        AzureConnectionResponse response = connectionService.createConnection(user, organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AzureConnectionControllerLogConstant.CONNECTION_CREATE_COMPLETED);

        return ResponseUtil.created(AzureConnectionControllerResponseConstant.RESPONSE_CONNECTION_CREATED, response);
    }

    /**
     * Replaces the name, tenant id and client id of a connection.
     */
    @PutMapping(AzureConnectionConstant.CONNECTION_PATH)
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Update Azure connection", description = "Replaces name, tenant id and client id; a new "
            + "tenant or client id resets the status to PENDING and clears the subscriptions, a rename alone "
            + "changes nothing else; organization admins only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header, or a "
                    + "body that is not valid JSON or holds an id that is not a UUID"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Connection not found or of another organization "
                    + "(AZR_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "Another connection of the organization has this name "
                    + "(AZR_STA_004) or this tenant and client id (AZR_STA_003)"),
            @ApiResponse(responseCode = "422", description = "Invalid request (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AzureConnectionResponse>> updateConnection(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID connectionId,
            @RequestBody @Valid AzureConnectionRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AzureConnectionControllerLogConstant.CONNECTION_UPDATE_REQUEST);

        AzureConnectionResponse response = connectionService.updateConnection(organizationId, connectionId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AzureConnectionControllerLogConstant.CONNECTION_UPDATE_COMPLETED);

        return ResponseUtil.ok(AzureConnectionControllerResponseConstant.RESPONSE_CONNECTION_UPDATED, response);
    }

    /**
     * Deletes a connection with its subscriptions.
     */
    @DeleteMapping(AzureConnectionConstant.CONNECTION_PATH)
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete Azure connection", description = "Removes a connection, its subscriptions and its "
            + "cached credential; organization admins only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Connection not found or of another organization "
                    + "(AZR_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "The connection is still in use (AZR_STA_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteConnection(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID connectionId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AzureConnectionControllerLogConstant.CONNECTION_DELETE_REQUEST);

        connectionService.deleteConnection(organizationId, connectionId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AzureConnectionControllerLogConstant.CONNECTION_DELETE_COMPLETED);

        return ResponseUtil.ok(AzureConnectionControllerResponseConstant.RESPONSE_CONNECTION_DELETED);
    }

    /**
     * Checks a connection against Azure now and returns the stored result.
     *
     * A refusal by Entra ID or Azure Resource Manager is the result of the check, not an error of
     * the request: it answers 200 with the status and lastError the check stored.
     */
    @PostMapping(AzureConnectionConstant.VERIFY_PATH)
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Verify Azure connection", description = "Exchanges an assertion for a token and lists "
            + "the subscriptions the identity can read; answers the stored result: CONNECTED with the "
            + "subscriptions, or PENDING or FAILING with lastError; organization admins only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Check ran; the result is in status and lastError"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Connection not found or of another organization "
                    + "(AZR_NOT_001)"),
            @ApiResponse(responseCode = "422", description = "The connection has no client id yet (AZR_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AzureConnectionResponse>> verifyConnection(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID connectionId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AzureConnectionControllerLogConstant.CONNECTION_VERIFY_REQUEST);

        AzureConnectionResponse response = connectionCheckService.verifyConnection(organizationId, connectionId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AzureConnectionControllerLogConstant.CONNECTION_VERIFY_COMPLETED);

        return ResponseUtil.ok(AzureConnectionControllerResponseConstant.RESPONSE_CONNECTION_VERIFIED, response);
    }
}
