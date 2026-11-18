package com.korur.oba.apps.kaya.release.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.apps.kaya.release.constant.controller.ReleaseControllerLogConstant;
import com.korur.oba.apps.kaya.release.constant.controller.ReleaseControllerResponseConstant;
import com.korur.oba.apps.kaya.release.dto.request.PackRequiredRequest;
import com.korur.oba.apps.kaya.release.dto.response.PackRequiredResponse;
import com.korur.oba.apps.kaya.release.service.RepositoryPackService;

/**
 * Marks a pack of the organization named by the X-Organization-ID header required, or no longer
 * required, through {@link RepositoryPackService}, which releases every repository whose composition
 * changes. It sits beside the pack order endpoints because it re-releases repositories, which the
 * pack domain cannot do without depending on releases. Only an OWNER may call it.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/packs/{id}/required")
@Tag(name = "Repository packs", description = "The packs a repository composes, in position order")
public class PackRequiredController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PackRequiredController.class);

    private final RepositoryPackService repositoryPackService;
    private final LoggingService loggingService;

    /**
     * Sets whether every repository of the organization composes a pack.
     */
    @PutMapping
    @RequiredUserAccess(OrganizationMemberRole.OWNER)
    @Operation(summary = "Set whether every repository composes a pack",
            description = "A required pack is composed into every repository of the organization, by name ahead of the "
                    + "repository's own packs. Every active repository whose composition changes is validated, then released "
                    + "in one transaction. An unchanged flag creates no release")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or the body malformed"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not an OWNER of the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such pack in the organization"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_003: a changed composition gets one path from two packs "
                    + "not on one chain; errorValues.violations lists them"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: required missing; KAY_CFG_031: marking it required "
                    + "while a descendant pack overrides one of its items, errorValues.violations lists each descendant "
                    + "(ownerIds[1]) and item; KAY_CFG_001: a changed composition "
                    + "breaks a rule, such as GUARDRAIL_SETTINGS_MISSING; errorValues.violations lists "
                    + "every violation"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PackRequiredResponse>> setRequired(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @Valid @RequestBody PackRequiredRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReleaseControllerLogConstant.PACK_REQUIRED_REQUEST,
                id, organizationId, request.required());
        PackRequiredResponse response = repositoryPackService.setPackRequired(user, organizationId, id, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReleaseControllerLogConstant.PACK_REQUIRED_COMPLETED,
                id, organizationId, response.required(), response.releaseCount());
        return ResponseUtil.ok(ReleaseControllerResponseConstant.RESPONSE_PACK_REQUIRED_SET, response);
    }
}
