package com.korur.oba.shrine.identity.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.identity.constant.controller.UserProfileControllerLogConstant;
import com.korur.oba.shrine.identity.constant.controller.UserProfileControllerResponseConstant;
import com.korur.oba.shrine.identity.dto.request.UserSelfUpdateRequest;
import com.korur.oba.shrine.identity.dto.response.UserDetailResponse;
import com.korur.oba.shrine.identity.dto.response.UserSyncResult;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserProfileQueryService;
import com.korur.oba.shrine.identity.service.UserProfileService;
import com.korur.oba.shrine.invitation.dto.request.InvitationDecisionRequest;
import com.korur.oba.shrine.invitation.dto.response.InvitationResponse;
import com.korur.oba.shrine.invitation.service.InvitationService;

/**
 * Purpose:
 * REST controller exposing self-service profile endpoints under /users/me.
 *
 * Bigger picture:
 * Handles the authenticated user's own profile lifecycle: initial sync from
 * Keycloak JWT (POST), read-only profile retrieval (GET), self-update (PUT),
 * self-disable (DELETE), and invitation decision processing. POST and GET
 * operate directly from the JWT SecurityContext, while PUT, DELETE, and
 * invitation decision require a resolved User via GetUser.
 * Separated from UserController which handles admin operations on other users.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2026-03-04
 * @updated 2026-04-09
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me")
@Tag(name = "Identity | User Profile", description = "Self-service user profile management")
public class UserProfileController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(UserProfileController.class);

    private final UserProfileService userProfileService;
    private final UserProfileQueryService userProfileQueryService;
    private final InvitationService invitationService;
    private final LoggingService loggingService;

    @PostMapping
    @Operation(summary = "Sync user from JWT", description = "Creates user if not exists, links keycloakId. Idempotent.")
    public ResponseEntity<CustomResponse<UserDetailResponse>> syncMe() {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                UserProfileControllerLogConstant.USER_PROFILE_SYNC_REQUEST);

        UserSyncResult result = userProfileService.syncUserFromJwt();

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                UserProfileControllerLogConstant.USER_PROFILE_SYNC_COMPLETED);

        if (result.created()) {
            return ResponseUtil.created(
                    UserProfileControllerResponseConstant.RESPONSE_USER_PROFILE_SYNC_CREATED, result.user());
        }
        return ResponseUtil.ok(
                UserProfileControllerResponseConstant.RESPONSE_USER_PROFILE_SYNC_COMPLETED, result.user());
    }

    @GetMapping
    @Operation(summary = "Get current user profile", description = "Read-only. Returns 404 if not synced yet.")
    public ResponseEntity<CustomResponse<UserDetailResponse>> getMe() {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                UserProfileControllerLogConstant.USER_PROFILE_GET_REQUEST);

        UserDetailResponse response = userProfileQueryService.getCurrentUser();

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                UserProfileControllerLogConstant.USER_PROFILE_GET_COMPLETED);

        return ResponseUtil.ok(UserProfileControllerResponseConstant.RESPONSE_USER_PROFILE_RETRIEVED, response);
    }

    @PutMapping
    @Operation(summary = "Update own profile")
    public ResponseEntity<CustomResponse<UserDetailResponse>> updateMe(
            @Parameter(hidden = true) @GetUser User user,
            @RequestBody @Valid UserSelfUpdateRequest request) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                UserProfileControllerLogConstant.USER_PROFILE_UPDATE_REQUEST);

        UserDetailResponse response = userProfileService.updateProfile(user.getId(), request);

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                UserProfileControllerLogConstant.USER_PROFILE_UPDATE_COMPLETED);

        return ResponseUtil.ok(UserProfileControllerResponseConstant.RESPONSE_USER_PROFILE_UPDATED, response);
    }

    @DeleteMapping
    @Operation(summary = "Disable own account")
    public ResponseEntity<CustomResponse<Void>> disableMe(
            @Parameter(hidden = true) @GetUser User user) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                UserProfileControllerLogConstant.USER_PROFILE_DELETE_REQUEST);

        userProfileService.disableOwnAccount(user.getId());

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                UserProfileControllerLogConstant.USER_PROFILE_DELETE_COMPLETED);

        return ResponseUtil.ok(UserProfileControllerResponseConstant.RESPONSE_USER_PROFILE_DELETED);
    }

    @PostMapping("/invitations/decision")
    @Operation(summary = "Accept or decline an invitation")
    public ResponseEntity<CustomResponse<InvitationResponse>> processInvitationDecision(
            @Parameter(hidden = true) @GetUser User user,
            @Valid @RequestBody InvitationDecisionRequest request) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                UserProfileControllerLogConstant.USER_PROFILE_INVITATION_DECISION_REQUEST);

        InvitationResponse response = invitationService.processDecision(user, request);

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                UserProfileControllerLogConstant.USER_PROFILE_INVITATION_DECISION_COMPLETED);

        return ResponseUtil.ok(
                UserProfileControllerResponseConstant.RESPONSE_INVITATION_DECISION_PROCESSED, response);
    }
}
