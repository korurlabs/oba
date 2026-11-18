package com.korur.oba.apps.kazan.comment.controller;

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
import com.korur.oba.apps.kazan.comment.constant.controller.CommentControllerLogConstant;
import com.korur.oba.apps.kazan.comment.constant.controller.CommentControllerResponseConstant;
import com.korur.oba.apps.kazan.comment.dto.request.CommentCreateRequest;
import com.korur.oba.apps.kazan.comment.dto.request.CommentUpdateRequest;
import com.korur.oba.apps.kazan.comment.dto.response.CommentDetailResponse;
import com.korur.oba.apps.kazan.comment.service.CommentQueryService;
import com.korur.oba.apps.kazan.comment.service.CommentService;
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
 * REST controller for comment management endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/boards/{boardId}/tickets/{ticketId}/comments")
@Tag(name = "Comments", description = "Comment management APIs")
public class CommentController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CommentController.class);

    private final CommentService commentService;
    private final CommentQueryService commentQueryService;
    private final LoggingService loggingService;

    /**
     * Creates a new comment on a ticket.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create comment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Board role too low (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or ticket or comment not found (KAZ_NOT_001, "
                    + "KAZ_NOT_002, KAZ_NOT_004, KAZ_NOT_007)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<CommentDetailResponse>> createComment(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID ticketId,
            @RequestBody @Valid CommentCreateRequest request) {
        // Log the incoming create request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                CommentControllerLogConstant.COMMENT_CREATE_REQUEST);

        // Delegate creation to the service layer.
        CommentDetailResponse response = commentService.createComment(user, organizationId, boardId, ticketId, request);

        // Log the completed create operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                CommentControllerLogConstant.COMMENT_CREATE_COMPLETED);

        // Return a 201 created response with payload.
        return ResponseUtil.created(CommentControllerResponseConstant.RESPONSE_COMMENT_CREATED, response);
    }

    /**
     * Lists comments for a ticket.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List comments")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or ticket or comment not found (KAZ_NOT_001, "
                    + "KAZ_NOT_002, KAZ_NOT_004, KAZ_NOT_007)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<CommentDetailResponse>>> listComments(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID ticketId) {
        // Log the incoming list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                CommentControllerLogConstant.COMMENT_LIST_REQUEST);

        // Query comments for the ticket.
        List<CommentDetailResponse> response = commentQueryService.listComments(user, organizationId, boardId, ticketId);

        // Log the completed list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                CommentControllerLogConstant.COMMENT_LIST_COMPLETED);

        // Return a 200 OK response with list payload.
        return ResponseUtil.ok(CommentControllerResponseConstant.RESPONSE_COMMENT_LIST_RETRIEVED, response);
    }

    /**
     * Updates a comment's content.
     */
    @PutMapping("/{commentId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update comment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Board role too low (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or ticket or comment not found (KAZ_NOT_001, "
                    + "KAZ_NOT_002, KAZ_NOT_004, KAZ_NOT_007)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<CommentDetailResponse>> updateComment(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID ticketId,
            @PathVariable UUID commentId,
            @RequestBody @Valid CommentUpdateRequest request) {
        // Log the incoming update request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                CommentControllerLogConstant.COMMENT_UPDATE_REQUEST);

        // Delegate update to the service layer.
        CommentDetailResponse response = commentService.updateComment(user, organizationId, boardId, ticketId, commentId, request);

        // Log the completed update operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                CommentControllerLogConstant.COMMENT_UPDATE_COMPLETED);

        // Return a 200 OK response with updated payload.
        return ResponseUtil.ok(CommentControllerResponseConstant.RESPONSE_COMMENT_UPDATED, response);
    }

    /**
     * Deletes a comment.
     */
    @DeleteMapping("/{commentId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete comment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Board role too low (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or ticket or comment not found (KAZ_NOT_001, "
                    + "KAZ_NOT_002, KAZ_NOT_004, KAZ_NOT_007)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteComment(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID ticketId,
            @PathVariable UUID commentId) {
        // Log the incoming delete request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                CommentControllerLogConstant.COMMENT_DELETE_REQUEST);

        // Delegate delete operation to the service layer.
        commentService.deleteComment(user, organizationId, boardId, ticketId, commentId);

        // Log the completed delete operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                CommentControllerLogConstant.COMMENT_DELETE_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(CommentControllerResponseConstant.RESPONSE_COMMENT_DELETED);
    }

}
