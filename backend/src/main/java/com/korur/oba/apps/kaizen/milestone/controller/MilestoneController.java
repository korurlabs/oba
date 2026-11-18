package com.korur.oba.apps.kaizen.milestone.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.milestone.constant.controller.MilestoneControllerLogConstant;
import com.korur.oba.apps.kaizen.milestone.constant.controller.MilestoneControllerResponseConstant;
import com.korur.oba.apps.kaizen.milestone.dto.request.MilestoneCreateRequest;
import com.korur.oba.apps.kaizen.milestone.dto.request.MilestoneQueryRequest;
import com.korur.oba.apps.kaizen.milestone.dto.request.MilestoneTicketsRequest;
import com.korur.oba.apps.kaizen.milestone.dto.request.MilestoneUpdateRequest;
import com.korur.oba.apps.kaizen.milestone.dto.response.MilestoneDetailResponse;
import com.korur.oba.apps.kaizen.milestone.dto.response.MilestoneListResponse;
import com.korur.oba.apps.kaizen.milestone.service.MilestoneQueryService;
import com.korur.oba.apps.kaizen.milestone.service.MilestoneService;
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
 * REST controller for milestone management endpoints.
 *
 * Reads need VIEWER on the board; writes need ADMIN on the milestone's board,
 * and link/unlink also need MEMBER on each ticket's board.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaizen/boards/{boardId}/milestones")
@Tag(name = "Milestones", description = "Milestone management APIs")
public class MilestoneController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MilestoneController.class);

    private final MilestoneService milestoneService;
    private final MilestoneQueryService milestoneQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the milestones of a board, optionally with those of related boards.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List milestones")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "membership not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<MilestoneListResponse>>> listMilestones(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @Valid @ModelAttribute @ParameterObject MilestoneQueryRequest query) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_LIST_REQUEST);

        List<MilestoneListResponse> response = milestoneQueryService.listMilestones(
                user, organizationId, boardId, query.getStatus(), query.isIncludeRelatedBoards());

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_LIST_COMPLETED);

        return ResponseUtil.ok(MilestoneControllerResponseConstant.RESPONSE_MILESTONE_LIST_RETRIEVED, response);
    }

    /**
     * Retrieves milestone details by id.
     */
    @GetMapping("/{milestoneId}")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get milestone details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or milestone "
                    + "not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MilestoneDetailResponse>> getMilestone(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID milestoneId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_GET_REQUEST);

        MilestoneDetailResponse response = milestoneQueryService.getMilestone(
                user, organizationId, boardId, milestoneId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_GET_COMPLETED);

        return ResponseUtil.ok(MilestoneControllerResponseConstant.RESPONSE_MILESTONE_RETRIEVED, response);
    }

    /**
     * Creates a milestone on the board.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create milestone")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request, or missing or malformed "
                    + "X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Insufficient board permissions, GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization disabled "
                    + "(ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "membership not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MilestoneDetailResponse>> createMilestone(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @RequestBody @Valid MilestoneCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_CREATE_REQUEST);

        MilestoneDetailResponse response = milestoneService.createMilestone(user, organizationId, boardId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_CREATE_COMPLETED);

        return ResponseUtil.created(MilestoneControllerResponseConstant.RESPONSE_MILESTONE_CREATED, response);
    }

    /**
     * Updates a milestone.
     */
    @PutMapping("/{milestoneId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update milestone")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Invalid request, or missing or malformed "
                    + "X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Insufficient board permissions, GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization disabled "
                    + "(ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or milestone "
                    + "not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MilestoneDetailResponse>> updateMilestone(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID milestoneId,
            @RequestBody @Valid MilestoneUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_UPDATE_REQUEST);

        MilestoneDetailResponse response = milestoneService.updateMilestone(
                user, organizationId, boardId, milestoneId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_UPDATE_COMPLETED);

        return ResponseUtil.ok(MilestoneControllerResponseConstant.RESPONSE_MILESTONE_UPDATED, response);
    }

    /**
     * Closes an open milestone.
     */
    @PostMapping("/{milestoneId}/close")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Close milestone")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Insufficient board permissions, GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization disabled "
                    + "(ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or milestone "
                    + "not found"),
            @ApiResponse(responseCode = "409", description = "Milestone already closed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MilestoneDetailResponse>> closeMilestone(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID milestoneId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_CLOSE_REQUEST);

        MilestoneDetailResponse response = milestoneService.closeMilestone(user, organizationId, boardId, milestoneId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_CLOSE_COMPLETED);

        return ResponseUtil.ok(MilestoneControllerResponseConstant.RESPONSE_MILESTONE_CLOSED, response);
    }

    /**
     * Reopens a closed milestone.
     */
    @PostMapping("/{milestoneId}/reopen")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Reopen milestone")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Insufficient board permissions, GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization disabled "
                    + "(ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or milestone "
                    + "not found"),
            @ApiResponse(responseCode = "409", description = "Milestone already open"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MilestoneDetailResponse>> reopenMilestone(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID milestoneId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_REOPEN_REQUEST);

        MilestoneDetailResponse response = milestoneService.reopenMilestone(user, organizationId, boardId, milestoneId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_REOPEN_COMPLETED);

        return ResponseUtil.ok(MilestoneControllerResponseConstant.RESPONSE_MILESTONE_REOPENED, response);
    }

    /**
     * Attaches tickets to the milestone.
     */
    @PostMapping("/{milestoneId}/link")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Link tickets to milestone")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Invalid request or ticket board not related, or missing "
                    + "or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Insufficient board permissions, GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization disabled "
                    + "(ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or milestone "
                    + "not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MilestoneDetailResponse>> linkTickets(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID milestoneId,
            @RequestBody @Valid MilestoneTicketsRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_LINK_REQUEST);

        MilestoneDetailResponse response = milestoneService.linkTickets(
                user, organizationId, boardId, milestoneId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_LINK_COMPLETED);

        return ResponseUtil.ok(MilestoneControllerResponseConstant.RESPONSE_MILESTONE_TICKETS_LINKED, response);
    }

    /**
     * Detaches tickets from the milestone.
     */
    @PostMapping("/{milestoneId}/unlink")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Unlink tickets from milestone")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Invalid request, or missing or malformed "
                    + "X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Insufficient board permissions, GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization disabled "
                    + "(ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or milestone "
                    + "not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MilestoneDetailResponse>> unlinkTickets(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID milestoneId,
            @RequestBody @Valid MilestoneTicketsRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_UNLINK_REQUEST);

        MilestoneDetailResponse response = milestoneService.unlinkTickets(
                user, organizationId, boardId, milestoneId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_UNLINK_COMPLETED);

        return ResponseUtil.ok(MilestoneControllerResponseConstant.RESPONSE_MILESTONE_TICKETS_UNLINKED, response);
    }

    /**
     * Soft-deletes a milestone and detaches its tickets.
     */
    @DeleteMapping("/{milestoneId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete milestone")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Insufficient board permissions, GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization disabled "
                    + "(ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or milestone "
                    + "not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteMilestone(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID milestoneId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_DELETE_REQUEST);

        milestoneService.deleteMilestone(user, organizationId, boardId, milestoneId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_DELETE_COMPLETED);

        return ResponseUtil.ok(MilestoneControllerResponseConstant.RESPONSE_MILESTONE_DELETED);
    }

    /**
     * Restores a soft-deleted milestone without its ticket links.
     */
    @PostMapping("/{milestoneId}/restore")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Restore milestone")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Milestone not deleted, or missing or malformed "
                    + "X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Insufficient board permissions, GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization disabled "
                    + "(ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or milestone "
                    + "not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> restoreMilestone(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID milestoneId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_RESTORE_REQUEST);

        milestoneService.restoreMilestone(user, organizationId, boardId, milestoneId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_RESTORE_COMPLETED);

        return ResponseUtil.ok(MilestoneControllerResponseConstant.RESPONSE_MILESTONE_RESTORED);
    }
}
