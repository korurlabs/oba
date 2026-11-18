package com.korur.oba.apps.kazan.organizationsetting.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.organizationsetting.constant.OrganizationSettingConstant;
import com.korur.oba.apps.kazan.organizationsetting.constant.controller.OrganizationSettingControllerLogConstant;
import com.korur.oba.apps.kazan.organizationsetting.constant.controller.OrganizationSettingControllerResponseConstant;
import com.korur.oba.apps.kazan.organizationsetting.dto.request.OrganizationSecurityBoardUpdateRequest;
import com.korur.oba.apps.kazan.organizationsetting.dto.response.OrganizationSettingResponse;
import com.korur.oba.apps.kazan.organizationsetting.service.OrganizationSettingService;
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
 * REST controller for the Kazan settings of an organization.
 *
 * It has no class-level path because it also serves the security board on the
 * platform path, which Kazan web releases before the move to the Kazan API still
 * call; the platform layer cannot serve it without depending on Kazan.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Organization settings", description = "Kazan settings of an organization")
public class OrganizationSettingController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OrganizationSettingController.class);

    private final OrganizationSettingService organizationSettingService;
    private final LoggingService loggingService;

    /**
     * Sets or clears the security board of an organization.
     */
    @PutMapping(OrganizationSettingConstant.SECURITY_BOARD_PATH)
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Update organization security board",
            description = "Sets the board that receives the organization's security tickets, or clears it with a "
                    + "null boardId; organization admins who are members of the board only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN of the organization (ORG_SEC_003)"),
            @ApiResponse(responseCode = "404", description = "Organization (ORG_NOT_001) or board (KAZ_NOT_001) not "
                    + "found, or not a member of the board (KAZ_NOT_002)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<OrganizationSettingResponse>> updateSecurityBoard(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @RequestBody @Valid OrganizationSecurityBoardUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                OrganizationSettingControllerLogConstant.SECURITY_BOARD_UPDATE_REQUEST);

        OrganizationSettingResponse response =
                organizationSettingService.updateSecurityBoard(organizationId, request.boardId(), user.getId());

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                OrganizationSettingControllerLogConstant.SECURITY_BOARD_UPDATE_COMPLETED);

        return ResponseUtil.ok(OrganizationSettingControllerResponseConstant.RESPONSE_SECURITY_BOARD_UPDATED, response);
    }

    /**
     * Sets or clears the security board on the platform path, with the same rules as
     * {@link #updateSecurityBoard}. Answers without data, as the platform path always did.
     */
    @Deprecated
    @PutMapping(OrganizationSettingConstant.LEGACY_SECURITY_BOARD_PATH)
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Update organization security board (platform path)", deprecated = true,
            description = "Same as PUT " + OrganizationSettingConstant.SECURITY_BOARD_PATH
                    + "; kept for Kazan web releases that still call it")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN of the organization (ORG_SEC_003)"),
            @ApiResponse(responseCode = "404", description = "Organization (ORG_NOT_001) or board (KAZ_NOT_001) not "
                    + "found, or not a member of the board (KAZ_NOT_002)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> updateSecurityBoardOnPlatformPath(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @RequestBody @Valid OrganizationSecurityBoardUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                OrganizationSettingControllerLogConstant.LEGACY_SECURITY_BOARD_UPDATE_REQUEST);

        organizationSettingService.updateSecurityBoard(organizationId, request.boardId(), user.getId());

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                OrganizationSettingControllerLogConstant.SECURITY_BOARD_UPDATE_COMPLETED);

        return ResponseUtil.ok(OrganizationSettingControllerResponseConstant.RESPONSE_SECURITY_BOARD_UPDATED);
    }
}
