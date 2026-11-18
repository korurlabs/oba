package com.korur.oba.apps.kazan.label.controller;

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

import com.korur.oba.apps.kazan.label.constant.controller.LabelControllerLogConstant;
import com.korur.oba.apps.kazan.label.constant.controller.LabelControllerResponseConstant;
import com.korur.oba.apps.kazan.label.dto.request.LabelCreateRequest;
import com.korur.oba.apps.kazan.label.dto.request.LabelUpdateRequest;
import com.korur.oba.apps.kazan.label.dto.response.LabelResponse;
import com.korur.oba.apps.kazan.label.service.LabelQueryService;
import com.korur.oba.apps.kazan.label.service.LabelService;
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
 * REST controller for label management endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/boards/{boardId}/labels")
@Tag(name = "Labels", description = "Label management APIs")
public class LabelController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LabelController.class);

    private final LabelService labelService;
    private final LabelQueryService labelQueryService;
    private final LoggingService loggingService;

    /**
     * Creates a new label within a board.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create label")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Board role too low (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or label not found (KAZ_NOT_001, KAZ_NOT_002, "
                    + "KAZ_NOT_008)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<LabelResponse>> createLabel(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @RequestBody @Valid LabelCreateRequest request) {
        // Log the incoming create request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                LabelControllerLogConstant.LABEL_CREATE_REQUEST);

        // Delegate creation to the service layer.
        LabelResponse response = labelService.createLabel(user, organizationId, boardId, request);

        // Log the completed create operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                LabelControllerLogConstant.LABEL_CREATE_COMPLETED);

        // Return a 201 created response with payload.
        return ResponseUtil.created(LabelControllerResponseConstant.RESPONSE_LABEL_CREATED, response);
    }

    /**
     * Lists labels for a board.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List labels")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or label not found (KAZ_NOT_001, KAZ_NOT_002, "
                    + "KAZ_NOT_008)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<LabelResponse>>> listLabels(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId) {
        // Log the incoming list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                LabelControllerLogConstant.LABEL_LIST_REQUEST);

        // Query labels for the board.
        List<LabelResponse> response = labelQueryService.listLabels(user, organizationId, boardId);

        // Log the completed list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                LabelControllerLogConstant.LABEL_LIST_COMPLETED);

        // Return a 200 OK response with list payload.
        return ResponseUtil.ok(LabelControllerResponseConstant.RESPONSE_LABEL_LIST_RETRIEVED, response);
    }

    /**
     * Updates a label's metadata.
     */
    @PutMapping("/{labelId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update label")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Board role too low (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or label not found (KAZ_NOT_001, KAZ_NOT_002, "
                    + "KAZ_NOT_008)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<LabelResponse>> updateLabel(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID labelId,
            @RequestBody @Valid LabelUpdateRequest request) {
        // Log the incoming update request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                LabelControllerLogConstant.LABEL_UPDATE_REQUEST);

        // Delegate update to the service layer.
        LabelResponse response = labelService.updateLabel(user, organizationId, boardId, labelId, request);

        // Log the completed update operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                LabelControllerLogConstant.LABEL_UPDATE_COMPLETED);

        // Return a 200 OK response with updated payload.
        return ResponseUtil.ok(LabelControllerResponseConstant.RESPONSE_LABEL_UPDATED, response);
    }

    /**
     * Deletes a label.
     */
    @DeleteMapping("/{labelId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete label")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Board role too low (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or label not found (KAZ_NOT_001, KAZ_NOT_002, "
                    + "KAZ_NOT_008)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteLabel(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID labelId) {
        // Log the incoming delete request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                LabelControllerLogConstant.LABEL_DELETE_REQUEST);

        // Delegate delete operation to the service layer.
        labelService.deleteLabel(user, organizationId, boardId, labelId);

        // Log the completed delete operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                LabelControllerLogConstant.LABEL_DELETE_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(LabelControllerResponseConstant.RESPONSE_LABEL_DELETED);
    }

}
