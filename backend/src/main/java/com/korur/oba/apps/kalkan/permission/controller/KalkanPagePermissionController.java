package com.korur.oba.apps.kalkan.permission.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.permission.constant.controller.KalkanPagePermissionControllerLogConstant;
import com.korur.oba.apps.kalkan.permission.constant.controller.KalkanPagePermissionControllerResponseConstant;
import com.korur.oba.apps.kalkan.permission.dto.request.KalkanPagePermissionRequest;
import com.korur.oba.apps.kalkan.permission.dto.response.KalkanPagePermissionResponse;
import com.korur.oba.apps.kalkan.permission.service.KalkanPermissionQueryService;
import com.korur.oba.apps.kalkan.permission.service.KalkanPermissionService;
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
 * The grants of a root page of the organization named by the X-Organization-ID header. The gate
 * admits every MEMBER; the services then require MANAGE on the root, which an OWNER always holds.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kalkan/pages/{pageId}/permissions")
@Tag(name = "Kalkan page permissions", description = "Levels granted on root pages")
public class KalkanPagePermissionController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(KalkanPagePermissionController.class);

    private final KalkanPermissionService kalkanPermissionService;
    private final KalkanPermissionQueryService kalkanPermissionQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the live grants of a root page.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the grants of a root page",
            description = "Every live grant of the root with the subject's name; needs MANAGE on the root")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Below MEMBER; KLK_SEC_001: below MANAGE on the root"),
            @ApiResponse(responseCode = "404", description = "KLK_NOT_001: no such page, or hidden from the caller"),
            @ApiResponse(responseCode = "422", description = "KLK_VAL_006: the page is not a root"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<KalkanPagePermissionResponse>>> getPermissions(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID pageId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPagePermissionControllerLogConstant.PERMISSION_LIST_REQUEST, user.getId(), pageId, organizationId);
        List<KalkanPagePermissionResponse> response = kalkanPermissionQueryService.getPermissions(user, organizationId,
                pageId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPagePermissionControllerLogConstant.PERMISSION_LIST_COMPLETED, response.size(), pageId, organizationId);
        return ResponseUtil.ok(KalkanPagePermissionControllerResponseConstant.RESPONSE_PERMISSIONS_RETRIEVED, response);
    }

    /**
     * Replaces every live grant of a root page.
     */
    @PutMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Replace the grants of a root page",
            description = "The body is the complete new list; an empty list revokes every grant. A subject listed with "
                    + "its current level keeps its grant; a removed subject or a changed level is revoked and kept on "
                    + "record; needs MANAGE on the root")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grants replaced; the live grants after the change"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a malformed body"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Below MEMBER; KLK_SEC_001: below MANAGE on the root"),
            @ApiResponse(responseCode = "404", description = "KLK_NOT_001: no such page, or hidden from the caller"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: a grant without a level; KLK_VAL_002: a "
                    + "subject missing, a group, listed twice or not an active member, errorValues.reason says which; "
                    + "KLK_VAL_003: a guest above VIEW; KLK_VAL_006: the page is not a root"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<KalkanPagePermissionResponse>>> replacePermissions(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID pageId,
            @RequestBody @NotNull List<@Valid @NotNull KalkanPagePermissionRequest> request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPagePermissionControllerLogConstant.PERMISSION_REPLACE_REQUEST, user.getId(), pageId, organizationId,
                request.size());
        List<KalkanPagePermissionResponse> response = kalkanPermissionService.replacePermissions(user, organizationId,
                pageId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPagePermissionControllerLogConstant.PERMISSION_REPLACE_COMPLETED, pageId, organizationId,
                response.size());
        return ResponseUtil.ok(KalkanPagePermissionControllerResponseConstant.RESPONSE_PERMISSIONS_REPLACED, response);
    }
}
