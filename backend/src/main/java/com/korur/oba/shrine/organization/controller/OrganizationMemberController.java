package com.korur.oba.shrine.organization.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.korur.oba.shrine.organization.constant.controller.OrganizationMemberControllerLogConstant;
import com.korur.oba.shrine.organization.constant.controller.OrganizationMemberControllerResponseConstant;
import com.korur.oba.shrine.organization.dto.request.OrganizationMemberRoleUpdateRequest;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberService;

/**
 * REST controller for organization member management endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/organizations/{organizationId}/members")
@Tag(name = "Organization Members", description = "Organization member management APIs")
public class OrganizationMemberController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OrganizationMemberController.class);

    private final OrganizationMemberService organizationMemberService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final LoggingService loggingService;

    /**
     * Lists all members in a specific organization. The caller must be an
     * active member of that organization.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List organization members")
    public ResponseEntity<CustomResponse<List<OrganizationMemberResponse>>> listMembers(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId) {
        // Log the incoming members list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationMemberControllerLogConstant.ORG_MEMBERS_LIST_REQUEST);

        // Query members for the organization.
        List<OrganizationMemberResponse> response = organizationMemberQueryService.listOrganizationMembers(user, organizationId);

        // Log the completed members list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationMemberControllerLogConstant.ORG_MEMBERS_LIST_REQUEST_COMPLETED);

        // Return a 200 OK response with members list.
        return ResponseUtil.ok(OrganizationMemberControllerResponseConstant.RESPONSE_ORGANIZATION_MEMBERS_RETRIEVED, response);
    }

    /**
     * Allows the authenticated user to leave an organization (self-removal).
     */
    @DeleteMapping("/me")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Leave organization")
    public ResponseEntity<CustomResponse<Void>> leaveOrganization(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId) {
        // Log the incoming leave organization request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationMemberControllerLogConstant.ORG_MEMBER_LEAVE_REQUEST);

        // Delegate leave operation to the service layer.
        organizationMemberService.leaveOrganization(user, organizationId);

        // Log the completed leave organization operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationMemberControllerLogConstant.ORG_MEMBER_LEAVE_REQUEST_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(OrganizationMemberControllerResponseConstant.RESPONSE_ORGANIZATION_LEFT);
    }

    /**
     * Removes an existing member from an organization.
     */
    @DeleteMapping("/{userId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Remove organization member")
    public ResponseEntity<CustomResponse<Void>> removeMember(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID userId) {
        // Log the incoming remove member request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationMemberControllerLogConstant.ORG_MEMBER_REMOVE_REQUEST);

        // Delegate member removal to the service layer.
        organizationMemberService.removeMember(user, organizationId, userId);

        // Log the completed remove member operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationMemberControllerLogConstant.ORG_MEMBER_REMOVE_REQUEST_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(OrganizationMemberControllerResponseConstant.RESPONSE_ORGANIZATION_MEMBER_REMOVED);
    }

    /**
     * Changes a member's role in an organization.
     */
    @PatchMapping("/{userId}/role")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Change organization member role")
    public ResponseEntity<CustomResponse<OrganizationMemberResponse>> changeMemberRole(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @RequestBody @Valid OrganizationMemberRoleUpdateRequest request) {
        // Log the incoming role change request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationMemberControllerLogConstant.ORG_MEMBER_ROLE_CHANGE_REQUEST);

        // Delegate role change to the service layer.
        OrganizationMemberResponse response = organizationMemberService.changeMemberRole(user, organizationId, userId, request);

        // Log the completed role change operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationMemberControllerLogConstant.ORG_MEMBER_ROLE_CHANGE_REQUEST_COMPLETED);

        // Return a 200 OK response with updated member payload.
        return ResponseUtil.ok(OrganizationMemberControllerResponseConstant.RESPONSE_ORGANIZATION_MEMBER_ROLE_CHANGED, response);
    }

    /**
     * Restores a soft-deleted member in an organization.
     */
    @PatchMapping("/{userId}/restore")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Restore organization member")
    public ResponseEntity<CustomResponse<Void>> restoreMember(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID userId) {
        // Log the incoming restore member request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationMemberControllerLogConstant.ORG_MEMBER_RESTORE_REQUEST);

        // Delegate member restoration to the service layer.
        organizationMemberService.restoreMember(user, organizationId, userId);

        // Log the completed restore member operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationMemberControllerLogConstant.ORG_MEMBER_RESTORE_REQUEST_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(OrganizationMemberControllerResponseConstant.RESPONSE_ORGANIZATION_MEMBER_RESTORED);
    }

    /**
     * Updates a member's lifecycle status (enable/disable).
     */
    @PatchMapping("/{userId}/status")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Update organization member status (enable/disable)")
    public ResponseEntity<CustomResponse<Void>> updateMemberStatus(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @RequestBody @Valid StatusUpdateRequest request) {
        // Log the incoming member status update request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationMemberControllerLogConstant.ORG_MEMBER_STATUS_UPDATE_REQUEST);

        OrganizationMemberStatus newStatus;
        try {
            newStatus = OrganizationMemberStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ExceptionDomainOrganizationError.VAL_INVALID_MEMBER_STATUS,
                    Map.of("status", request.status()));
        }

        // Delegate member status update to the service layer.
        organizationMemberService.updateMemberStatus(user, organizationId, userId, newStatus);

        // Log the completed member status update operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationMemberControllerLogConstant.ORG_MEMBER_STATUS_UPDATE_REQUEST_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(OrganizationMemberControllerResponseConstant.RESPONSE_ORGANIZATION_MEMBER_STATUS_UPDATED);
    }
}
