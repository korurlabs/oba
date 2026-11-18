package com.korur.oba.apps.otak.icp.controller;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
import org.springdoc.core.annotations.ParameterObject;

import jakarta.validation.Valid;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.icp.constant.controller.IcpControllerLogConstant;
import com.korur.oba.apps.otak.icp.constant.controller.IcpControllerResponseConstant;
import com.korur.oba.apps.otak.icp.dto.request.IcpCreateRequest;
import com.korur.oba.apps.otak.icp.dto.request.IcpQueryRequest;
import com.korur.oba.apps.otak.icp.dto.request.IcpUpdateRequest;
import com.korur.oba.apps.otak.icp.dto.response.IcpDetailResponse;
import com.korur.oba.apps.otak.icp.dto.response.IcpListResponse;
import com.korur.oba.apps.otak.icp.service.IcpQueryService;
import com.korur.oba.apps.otak.icp.service.IcpService;
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
 * REST controller for the ICPs (Ideal Customer Profiles) of an organization: the kinds of
 * company it wants to win. Every member reads and changes them; only an admin deletes one.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/otak/icps")
@Tag(name = "Otak ICPs", description = "Ideal Customer Profiles of an organization")
public class IcpController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(IcpController.class);

    private final IcpService icpService;
    private final IcpQueryService icpQueryService;
    private final LoggingService loggingService;

    /**
     * Lists one page of the ICPs by name.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List ICPs",
            description = "One page of the organization's ICPs by name, with an optional name search. The order is "
                    + "fixed; sortBy and sortDirection are ignored.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "422", description = "Invalid paging or search (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<IcpListResponse>>> listIcps(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject IcpQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, IcpControllerLogConstant.ICP_LIST_REQUEST);

        Page<IcpListResponse> response = icpQueryService.list(organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, IcpControllerLogConstant.ICP_LIST_COMPLETED);

        return ResponseUtil.ok(IcpControllerResponseConstant.RESPONSE_ICP_LIST_RETRIEVED, response);
    }

    /**
     * Creates an ICP.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create ICP", description = "Creates an ICP of the organization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "409", description = "Another ICP of the organization has this name, case "
                    + "ignored (OTK_STA_005)"),
            @ApiResponse(responseCode = "422", description = "Minimum company size above the maximum (OTK_VAL_001), "
                    + "or invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<IcpDetailResponse>> createIcp(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid IcpCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, IcpControllerLogConstant.ICP_CREATE_REQUEST);

        IcpDetailResponse response = icpService.create(organizationId, user, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, IcpControllerLogConstant.ICP_CREATE_COMPLETED);

        return ResponseUtil.created(IcpControllerResponseConstant.RESPONSE_ICP_CREATED, response);
    }

    /**
     * Reads one ICP.
     */
    @GetMapping("/{icpId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Get ICP", description = "Every field of one ICP of the organization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "ICP not found or of another organization (OTK_NOT_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<IcpDetailResponse>> getIcp(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID icpId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, IcpControllerLogConstant.ICP_GET_REQUEST);

        IcpDetailResponse response = icpQueryService.get(organizationId, icpId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, IcpControllerLogConstant.ICP_GET_COMPLETED);

        return ResponseUtil.ok(IcpControllerResponseConstant.RESPONSE_ICP_RETRIEVED, response);
    }

    /**
     * Replaces every field of an ICP.
     */
    @PutMapping("/{icpId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update ICP",
            description = "Replaces every field of an ICP; null clears an optional field.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "ICP not found or of another organization (OTK_NOT_004)"),
            @ApiResponse(responseCode = "409", description = "Another ICP of the organization has this name, case "
                    + "ignored (OTK_STA_005)"),
            @ApiResponse(responseCode = "422", description = "Minimum company size above the maximum (OTK_VAL_001), "
                    + "or invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<IcpDetailResponse>> updateIcp(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID icpId,
            @RequestBody @Valid IcpUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, IcpControllerLogConstant.ICP_UPDATE_REQUEST);

        IcpDetailResponse response = icpService.update(organizationId, icpId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, IcpControllerLogConstant.ICP_UPDATE_COMPLETED);

        return ResponseUtil.ok(IcpControllerResponseConstant.RESPONSE_ICP_UPDATED, response);
    }

    /**
     * Deletes an ICP; its relations stay and lose the link.
     */
    @DeleteMapping("/{icpId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete ICP",
            description = "Removes an ICP; the relations linked to it stay and lose the link. Organization admins "
                    + "and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "ICP not found or of another organization (OTK_NOT_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteIcp(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID icpId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, IcpControllerLogConstant.ICP_DELETE_REQUEST);

        icpService.delete(organizationId, icpId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, IcpControllerLogConstant.ICP_DELETE_COMPLETED);

        return ResponseUtil.ok(IcpControllerResponseConstant.RESPONSE_ICP_DELETED);
    }
}
