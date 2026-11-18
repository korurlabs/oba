package com.korur.oba.apps.kaya.role.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.korur.oba.apps.kaya.role.constant.controller.RoleControllerLogConstant;
import com.korur.oba.apps.kaya.role.constant.controller.RoleControllerResponseConstant;
import com.korur.oba.apps.kaya.role.dto.request.RoleQueryRequest;
import com.korur.oba.apps.kaya.role.dto.response.RoleDetailResponse;
import com.korur.oba.apps.kaya.role.dto.response.RoleListResponse;
import com.korur.oba.apps.kaya.role.service.RoleQueryService;

/**
 * The roles of the versions of the organization named by the X-Organization-ID header, read by every
 * member through {@link RoleQueryService}; roles are written only by parsing a version's files.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/roles")
@Tag(name = "Roles", description = "Role files of pack and project versions")
public class RoleController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RoleController.class);

    private final RoleQueryService roleQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the roles of the given versions.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the roles of versions",
            description = "Not paged; ordered by version, path and id. A version id of another organization matches nothing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a version id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: no version id or more than 20"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<RoleListResponse>>> getRoles(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject RoleQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RoleControllerLogConstant.ROLE_LIST_REQUEST,
                request.getVersionId().size(), organizationId);
        List<RoleListResponse> response = roleQueryService.getRoles(organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RoleControllerLogConstant.ROLE_LIST_COMPLETED,
                response.size(), organizationId);
        return ResponseUtil.ok(RoleControllerResponseConstant.RESPONSE_ROLES_RETRIEVED, response);
    }

    /**
     * Reads one role with its content.
     */
    @GetMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a role", description = "The index fields and the whole file as UTF-8 text")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID or the id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the organization has no such role"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<RoleDetailResponse>> getRole(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RoleControllerLogConstant.ROLE_GET_REQUEST, id, organizationId);
        RoleDetailResponse response = roleQueryService.getRole(organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RoleControllerLogConstant.ROLE_GET_COMPLETED, id, organizationId);
        return ResponseUtil.ok(RoleControllerResponseConstant.RESPONSE_ROLE_RETRIEVED, response);
    }
}
