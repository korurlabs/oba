package com.korur.oba.shrine.invitation.controller;

// 1. Framework-level
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import com.korur.oba.shrine.invitation.constant.controller.InvitationControllerLogConstant;
import com.korur.oba.shrine.invitation.constant.controller.InvitationControllerResponseConstant;
import com.korur.oba.shrine.invitation.dto.request.InvitationCreateRequest;
import com.korur.oba.shrine.invitation.dto.response.InvitationResponse;
import com.korur.oba.shrine.invitation.service.InvitationQueryService;
import com.korur.oba.shrine.invitation.service.InvitationService;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * REST controller for organization invitation operations.
 *
 * All invitations are created with PENDING status. Users accept or decline
 * invitations via the POST /users/me/invitations/decision endpoint.
 */
@RestController
@RequestMapping("/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitations", description = "Organization invitation management")
public class InvitationController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(InvitationController.class);

    private final InvitationService invitationService;
    private final InvitationQueryService invitationQueryService;
    private final LoggingService loggingService;

    /**
     * Creates an invitation to join an organization.
     * Requires ADMIN role in the organization.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Create organization invitation")
    public ResponseEntity<CustomResponse<InvitationResponse>> createInvitation(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @RequestBody InvitationCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                InvitationControllerLogConstant.INVITATION_CREATE_REQUEST);

        InvitationResponse response = invitationService.createInvitation(user, organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                InvitationControllerLogConstant.INVITATION_CREATE_REQUEST_COMPLETED);

        return ResponseUtil.created(InvitationControllerResponseConstant.RESPONSE_INVITATION_CREATED, response);
    }

    /**
     * Lists all pending invitations for an organization.
     * Requires ADMIN role in the organization.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "List pending invitations")
    public ResponseEntity<CustomResponse<List<InvitationResponse>>> listInvitations(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                InvitationControllerLogConstant.INVITATION_LIST_REQUEST);

        List<InvitationResponse> response = invitationQueryService.listPendingInvitations(user, organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                InvitationControllerLogConstant.INVITATION_LIST_REQUEST_COMPLETED);

        return ResponseUtil.ok(InvitationControllerResponseConstant.RESPONSE_INVITATIONS_RETRIEVED, response);
    }

    /**
     * Revokes a pending invitation.
     * Requires ADMIN role in the organization.
     */
    @DeleteMapping("/{invitationId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Revoke invitation")
    public ResponseEntity<CustomResponse<Void>> revokeInvitation(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID invitationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                InvitationControllerLogConstant.INVITATION_REVOKE_REQUEST, invitationId);

        invitationService.revokeInvitation(user, organizationId, invitationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                InvitationControllerLogConstant.INVITATION_REVOKE_REQUEST_COMPLETED, invitationId);

        return ResponseUtil.ok(InvitationControllerResponseConstant.RESPONSE_INVITATION_REVOKED);
    }
}
