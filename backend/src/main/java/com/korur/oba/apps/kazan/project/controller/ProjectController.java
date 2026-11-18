package com.korur.oba.apps.kazan.project.controller;

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

import com.korur.oba.apps.kazan.project.constant.controller.ProjectControllerLogConstant;
import com.korur.oba.apps.kazan.project.constant.controller.ProjectControllerResponseConstant;
import com.korur.oba.apps.kazan.project.dto.request.ProjectCreateRequest;
import com.korur.oba.apps.kazan.project.dto.request.ProjectUpdateRequest;
import com.korur.oba.apps.kazan.project.dto.response.ProjectDetailResponse;
import com.korur.oba.apps.kazan.project.dto.response.ProjectListResponse;
import com.korur.oba.apps.kazan.project.service.ProjectQueryService;
import com.korur.oba.apps.kazan.project.service.ProjectService;
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

/**
 * REST controller for project management endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/boards/{boardId}/projects")
@Tag(name = "Projects", description = "Project management APIs")
public class ProjectController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ProjectController.class);

    private final ProjectService projectService;
    private final ProjectQueryService projectQueryService;
    private final LoggingService loggingService;

    /**
     * Creates a new project within a board.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create project")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "GUEST role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), or the caller is not a member of it (KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ProjectDetailResponse>> createProject(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @RequestBody @Valid ProjectCreateRequest request) {
        // Log the incoming create request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                ProjectControllerLogConstant.PROJECT_CREATE_REQUEST);

        // Delegate creation to the service layer.
        ProjectDetailResponse response = projectService.createProject(user, organizationId, boardId, request);

        // Log the completed create operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                ProjectControllerLogConstant.PROJECT_CREATE_COMPLETED);

        // Return a 201 created response with payload.
        return ResponseUtil.created(ProjectControllerResponseConstant.RESPONSE_PROJECT_CREATED, response);
    }

    /**
     * Lists projects for a board.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List projects")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), or the caller is not a member of it (KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<ProjectListResponse>>> listProjects(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId) {
        // Log the incoming list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                ProjectControllerLogConstant.PROJECT_LIST_REQUEST);

        // Query projects for the board.
        List<ProjectListResponse> response = projectQueryService.listProjects(user, organizationId, boardId);

        // Log the completed list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                ProjectControllerLogConstant.PROJECT_LIST_COMPLETED);

        // Return a 200 OK response with list payload.
        return ResponseUtil.ok(ProjectControllerResponseConstant.RESPONSE_PROJECT_LIST_RETRIEVED, response);
    }

    /**
     * Retrieves project details by id.
     */
    @GetMapping("/{projectId}")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get project details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), project not found (KAZ_NOT_006), or the caller is not a member of the board "
                    + "(KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ProjectDetailResponse>> getProject(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID projectId) {
        // Log the incoming detail request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                ProjectControllerLogConstant.PROJECT_GET_REQUEST);

        // Query project detail.
        ProjectDetailResponse response = projectQueryService.getProject(user, organizationId, boardId, projectId);

        // Log the completed detail operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                ProjectControllerLogConstant.PROJECT_GET_COMPLETED);

        // Return a 200 OK response with detail payload.
        return ResponseUtil.ok(ProjectControllerResponseConstant.RESPONSE_PROJECT_RETRIEVED, response);
    }

    /**
     * Updates project metadata.
     */
    @PutMapping("/{projectId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update project")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "GUEST role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), project not found (KAZ_NOT_006), or the caller is not a member of the board "
                    + "(KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ProjectDetailResponse>> updateProject(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID projectId,
            @RequestBody @Valid ProjectUpdateRequest request) {
        // Log the incoming update request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                ProjectControllerLogConstant.PROJECT_UPDATE_REQUEST);

        // Delegate update to the service layer.
        ProjectDetailResponse response = projectService.updateProject(
                user, organizationId, boardId, projectId, request);

        // Log the completed update operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                ProjectControllerLogConstant.PROJECT_UPDATE_COMPLETED);

        // Return a 200 OK response with updated payload.
        return ResponseUtil.ok(ProjectControllerResponseConstant.RESPONSE_PROJECT_UPDATED, response);
    }

    /**
     * Deletes a project.
     */
    @DeleteMapping("/{projectId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete project")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "GUEST role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), project not found (KAZ_NOT_006), or the caller is not a member of the board "
                    + "(KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteProject(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID projectId) {
        // Log the incoming delete request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                ProjectControllerLogConstant.PROJECT_DELETE_REQUEST);

        // Delegate delete operation to the service layer.
        projectService.deleteProject(user, organizationId, boardId, projectId);

        // Log the completed delete operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                ProjectControllerLogConstant.PROJECT_DELETE_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(ProjectControllerResponseConstant.RESPONSE_PROJECT_DELETED);
    }

}
