package com.korur.oba.shrine.organization.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.dto.request.StatusUpdateRequest;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.constant.controller.OrganizationControllerLogConstant;
import com.korur.oba.shrine.organization.constant.controller.OrganizationControllerResponseConstant;
import com.korur.oba.shrine.organization.dto.request.OrganizationCreateRequest;
import com.korur.oba.shrine.organization.dto.request.OrganizationUpdateRequest;
import com.korur.oba.shrine.organization.dto.response.OrganizationDetailResponse;
import com.korur.oba.shrine.organization.dto.response.OrganizationListResponse;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;
import com.korur.oba.shrine.organization.service.OrganizationService;

/**
 * REST controller for organization management endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/organizations")
@Tag(name = "Organizations", description = "Organization management APIs")
public class OrganizationController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OrganizationController.class);

    private final OrganizationService organizationService;
    private final OrganizationQueryService organizationQueryService;
    private final LoggingService loggingService;

    /**
     * Creates a new organization for the current user.
     */
    @PostMapping
    @Operation(summary = "Create organization")
    public ResponseEntity<CustomResponse<OrganizationDetailResponse>> createOrganization(
            @Parameter(hidden = true) @GetUser User user,
            @RequestBody @Valid OrganizationCreateRequest request) {
        // Log the incoming create request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationControllerLogConstant.ORG_CREATE_REQUEST);

        // Delegate creation to the service layer.
        OrganizationDetailResponse response = organizationService.createOrganization(user, request);

        // Log the completed create operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationControllerLogConstant.ORG_CREATE_REQUEST_COMPLETED);

        // Return a 201 created response with payload.
        return ResponseUtil.created(OrganizationControllerResponseConstant.RESPONSE_ORGANIZATION_CREATED, response);
    }

    /**
     * Lists organizations for the current user.
     */
    @GetMapping
    @Operation(summary = "List organizations")
    public ResponseEntity<CustomResponse<List<OrganizationListResponse>>> listOrganizations(
            @Parameter(hidden = true) @GetUser User user) {
        // Log the incoming list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationControllerLogConstant.ORG_LIST_REQUEST);

        // Query organizations for the current user.
        List<OrganizationListResponse> response = organizationQueryService.listOrganizations(user);

        // Log the completed list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationControllerLogConstant.ORG_LIST_REQUEST_COMPLETED);

        // Return a 200 OK response with list payload.
        return ResponseUtil.ok(OrganizationControllerResponseConstant.RESPONSE_ORGANIZATIONS_RETRIEVED, response);
    }

    /**
     * Retrieves organization details by id.
     *
     * The detail is a screen of the Ulak app, which is for the owners and admins
     * of an organization, so the minimum role is {@link OrganizationMemberRole#ADMIN}.
     * The access interceptor is the only check: the service adds none.
     */
    @GetMapping("/{organizationId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Get organization details")
    public ResponseEntity<CustomResponse<OrganizationDetailResponse>> getOrganization(
            @PathVariable UUID organizationId) {
        // Log the incoming detail request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationControllerLogConstant.ORG_DETAIL_REQUEST);

        // Query organization detail.
        OrganizationDetailResponse response = organizationQueryService.getOrganization(organizationId);

        // Log the completed detail operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationControllerLogConstant.ORG_DETAIL_REQUEST_COMPLETED);

        // Return a 200 OK response with detail payload.
        return ResponseUtil.ok(OrganizationControllerResponseConstant.RESPONSE_ORGANIZATION_RETRIEVED, response);
    }

    /**
     * Updates organization metadata.
     */
    @PutMapping("/{organizationId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Update organization")
    public ResponseEntity<CustomResponse<OrganizationDetailResponse>> updateOrganization(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @RequestBody @Valid OrganizationUpdateRequest request) {
        // Log the incoming update request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationControllerLogConstant.ORG_UPDATE_REQUEST);

        // Delegate update to the service layer.
        OrganizationDetailResponse response = organizationService.updateOrganization(user, organizationId, request);

        // Log the completed update operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationControllerLogConstant.ORG_UPDATE_REQUEST_COMPLETED);

        // Return a 200 OK response with updated payload.
        return ResponseUtil.ok(OrganizationControllerResponseConstant.RESPONSE_ORGANIZATION_UPDATED, response);
    }

    /**
     * Updates an organization's lifecycle status (enable/disable).
     */
    @PatchMapping("/{organizationId}/status")
    @Operation(summary = "Update organization status (enable/disable)")
    public ResponseEntity<CustomResponse<Void>> updateOrganizationStatus(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @RequestBody @Valid StatusUpdateRequest request) {
        // Log the incoming status update request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationControllerLogConstant.ORG_STATUS_UPDATE_REQUEST);

        OrganizationStatus newStatus;
        try {
            newStatus = OrganizationStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ExceptionDomainOrganizationError.VAL_INVALID_ORGANIZATION_STATUS,
                    Map.of("status", request.status()));
        }

        // Delegate status update to the service layer.
        organizationService.updateOrganizationStatus(user, organizationId, newStatus);

        // Log the completed status update operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationControllerLogConstant.ORG_STATUS_UPDATE_REQUEST_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(OrganizationControllerResponseConstant.RESPONSE_ORGANIZATION_STATUS_UPDATED);
    }

    /**
     * Permanently deletes an organization (anonymize + soft-delete).
     */
    @DeleteMapping("/{organizationId}")
    @RequiredUserAccess(OrganizationMemberRole.OWNER)
    @Operation(summary = "Delete organization")
    public ResponseEntity<CustomResponse<Void>> deleteOrganization(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId) {
        // Log the incoming delete request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationControllerLogConstant.ORG_DELETE_REQUEST);

        // Delegate delete operation to the service layer.
        organizationService.deleteOrganization(user, organizationId);

        // Log the completed delete operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationControllerLogConstant.ORG_DELETE_REQUEST_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(OrganizationControllerResponseConstant.RESPONSE_ORGANIZATION_DELETED);
    }
}
