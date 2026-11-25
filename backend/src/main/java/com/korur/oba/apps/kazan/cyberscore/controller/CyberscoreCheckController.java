package com.korur.oba.apps.kazan.cyberscore.controller;

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
import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.constant.controller.CyberscoreCheckControllerLogConstant;
import com.korur.oba.apps.kazan.cyberscore.constant.controller.CyberscoreCheckControllerResponseConstant;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CyberscoreCheckOutcomeRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CyberscoreCheckQueryRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CyberscoreCheckRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CyberscoreCheckStatusRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreCheckResponse;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreCheckQueryService;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreCheckService;
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
 * REST controller for the checks an organization's cyberscore is computed
 * from. Members and above may read them, guests may not; only organization
 * admins may change them.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/cyberscore/checks")
@Tag(name = "Cyberscore checks", description = "Security checks that make up the organization's cyberscore")
public class CyberscoreCheckController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CyberscoreCheckController.class);

    private final CyberscoreCheckService checkService;
    private final CyberscoreCheckQueryService checkQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the checks in display order, optionally of one status.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List checks", description = "Every check of the organization, by category (uncategorized "
            + "last), sort order and name; optionally of one status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "A guest of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "422", description = "Unknown status (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<CyberscoreCheckResponse>>> listChecks(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject CyberscoreCheckQueryRequest query) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreCheckControllerLogConstant.CHECK_LIST_REQUEST);

        List<CyberscoreCheckResponse> response = checkQueryService.listChecks(organizationId, query);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreCheckControllerLogConstant.CHECK_LIST_COMPLETED);

        return ResponseUtil.ok(CyberscoreCheckControllerResponseConstant.RESPONSE_CHECK_LIST_RETRIEVED, response);
    }

    /**
     * Creates a check and recalculates the score.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Create check", description = "Adds a check and recalculates the cyberscore; "
            + "organization admins only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "409", description = "The organization already has a check with this name "
                    + "(KAZ_STA_020)"),
            @ApiResponse(responseCode = "422", description = "Invalid request (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<CyberscoreCheckResponse>> createCheck(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid CyberscoreCheckRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreCheckControllerLogConstant.CHECK_CREATE_REQUEST);

        CyberscoreCheckResponse response = checkService.createCheck(user, organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreCheckControllerLogConstant.CHECK_CREATE_COMPLETED);

        return ResponseUtil.created(CyberscoreCheckControllerResponseConstant.RESPONSE_CHECK_CREATED, response);
    }

    /**
     * Replaces the fields of a check.
     */
    @PutMapping("/{checkId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Update check", description = "Replaces the fields of a check; recalculates the "
            + "cyberscore when weight, category or outcome changed; organization admins only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Check not found or of another organization "
                    + "(KAZ_NOT_020)"),
            @ApiResponse(responseCode = "409", description = "Another check of the organization has this name "
                    + "(KAZ_STA_020)"),
            @ApiResponse(responseCode = "422", description = "Invalid request (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<CyberscoreCheckResponse>> updateCheck(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID checkId,
            @RequestBody @Valid CyberscoreCheckRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreCheckControllerLogConstant.CHECK_UPDATE_REQUEST);

        CyberscoreCheckResponse response = checkService.updateCheck(user, organizationId, checkId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreCheckControllerLogConstant.CHECK_UPDATE_COMPLETED);

        return ResponseUtil.ok(CyberscoreCheckControllerResponseConstant.RESPONSE_CHECK_UPDATED, response);
    }

    /**
     * Records a check's current outcome.
     */
    @PutMapping("/{checkId}/outcome")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Set check outcome", description = "Records the outcome, sets the status it implies and "
            + "recalculates the cyberscore when the outcome changed; organization admins only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Check not found or of another organization "
                    + "(KAZ_NOT_020)"),
            @ApiResponse(responseCode = "422", description = "Invalid request (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<CyberscoreCheckResponse>> setCheckOutcome(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID checkId,
            @RequestBody @Valid CyberscoreCheckOutcomeRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreCheckControllerLogConstant.CHECK_OUTCOME_REQUEST);

        CyberscoreCheckResponse response = checkService.setCheckOutcome(user, organizationId, checkId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreCheckControllerLogConstant.CHECK_OUTCOME_COMPLETED);

        return ResponseUtil.ok(CyberscoreCheckControllerResponseConstant.RESPONSE_CHECK_OUTCOME_UPDATED, response);
    }

    /**
     * Sets a check's workflow status.
     */
    @PutMapping("/{checkId}/status")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Set check status", description = "Sets the workflow status, which never changes the "
            + "cyberscore; organization admins only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Check not found or of another organization "
                    + "(KAZ_NOT_020)"),
            @ApiResponse(responseCode = "422", description = "Invalid request (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<CyberscoreCheckResponse>> setCheckStatus(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID checkId,
            @RequestBody @Valid CyberscoreCheckStatusRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreCheckControllerLogConstant.CHECK_STATUS_REQUEST);

        CyberscoreCheckResponse response = checkService.setCheckStatus(user, organizationId, checkId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreCheckControllerLogConstant.CHECK_STATUS_COMPLETED);

        return ResponseUtil.ok(CyberscoreCheckControllerResponseConstant.RESPONSE_CHECK_STATUS_UPDATED, response);
    }

    /**
     * Deletes a check and recalculates the score.
     */
    @DeleteMapping("/{checkId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete check", description = "Removes a check and recalculates the cyberscore; "
            + "organization admins only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), "
                    + "not a member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Check not found or of another organization "
                    + "(KAZ_NOT_020)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteCheck(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID checkId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreCheckControllerLogConstant.CHECK_DELETE_REQUEST);

        checkService.deleteCheck(user, organizationId, checkId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreCheckControllerLogConstant.CHECK_DELETE_COMPLETED);

        return ResponseUtil.ok(CyberscoreCheckControllerResponseConstant.RESPONSE_CHECK_DELETED);
    }
}
