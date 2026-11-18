package com.korur.oba.apps.kazan.backlog.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

// 3. Java SDK
import java.util.UUID;

import com.korur.oba.apps.kazan.backlog.constant.controller.BacklogControllerLogConstant;
import com.korur.oba.apps.kazan.backlog.constant.controller.BacklogControllerResponseConstant;
import com.korur.oba.apps.kazan.backlog.dto.response.BacklogDetailResponse;
import com.korur.oba.apps.kazan.backlog.service.BacklogQueryService;
import com.korur.oba.apps.kazan.backlog.service.BacklogService;
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
 * REST controller for backlog management endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/boards/{boardId}/backlog")
@Tag(name = "Backlogs", description = "Backlog management APIs")
public class BacklogController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BacklogController.class);

    private final BacklogService backlogService;
    private final BacklogQueryService backlogQueryService;
    private final LoggingService loggingService;

    /**
     * Retrieves the backlog for a board.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get backlog")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or backlog or ticket not found (KAZ_NOT_001, "
                    + "KAZ_NOT_002, KAZ_NOT_004, KAZ_NOT_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<BacklogDetailResponse>> getBacklog(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId) {
        // Log the incoming detail request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BacklogControllerLogConstant.BACKLOG_GET_REQUEST);

        // Query backlog detail.
        BacklogDetailResponse response = backlogQueryService.getBacklog(user, organizationId, boardId);

        // Log the completed detail operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BacklogControllerLogConstant.BACKLOG_GET_COMPLETED);

        // Return a 200 OK response with detail payload.
        return ResponseUtil.ok(BacklogControllerResponseConstant.RESPONSE_BACKLOG_RETRIEVED, response);
    }

    /**
     * Adds a ticket to the backlog.
     */
    @PostMapping("/tickets/{ticketId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Add ticket to backlog")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Board role too low (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or backlog or ticket not found (KAZ_NOT_001, "
                    + "KAZ_NOT_002, KAZ_NOT_004, KAZ_NOT_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> addTicketToBacklog(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID ticketId) {
        // Log the incoming update request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BacklogControllerLogConstant.BACKLOG_UPDATE_REQUEST);

        // Delegate ticket addition to the service layer.
        backlogService.addTicketToBacklog(user, organizationId, boardId, ticketId);

        // Log the completed update operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BacklogControllerLogConstant.BACKLOG_UPDATE_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(BacklogControllerResponseConstant.RESPONSE_BACKLOG_UPDATED);
    }

}
