package com.korur.oba.apps.kaya.release.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.apps.kaya.release.constant.controller.ReleaseControllerLogConstant;
import com.korur.oba.apps.kaya.release.constant.controller.ReleaseControllerResponseConstant;
import com.korur.oba.apps.kaya.release.dto.response.EffectiveItemResponse;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;

/**
 * A pack's effective config: what the pack sets itself and, on request, what it inherits from its
 * ancestors. It lives beside the composition because it walks the same chain and reads the version
 * domain, which the pack domain may not.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/packs/{id}/effective")
@Tag(name = "Repository packs", description = "The packs a repository composes, in position order")
public class PackEffectiveController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PackEffectiveController.class);

    private final ReleaseQueryService releaseQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the items a pack stores, with the overrides they make, and optionally the items it inherits.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a pack's effective items",
            description = "The pack's own items at a version or draft, each with the override it makes of an ancestor's "
                    + "item and its status (CURRENT, PARENT_UPDATED, PARENT_REMOVED). With inherited=true also every item "
                    + "an ancestor's current version gives that the pack does not store, from the nearest such ancestor, "
                    + "with its lock (REQUIRED, GUARDRAIL) when the pack may not override it")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a parameter "
                    + "malformed"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such pack in the organization, or the version "
                    + "is not one of the pack's"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<EffectiveItemResponse>>> getEffective(
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @Parameter(description = "A version or draft of the pack; the current version when absent",
                    example = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b")
            @RequestParam(required = false) UUID versionId,
            @Parameter(description = "Whether to add the inherited items", example = "false")
            @RequestParam(defaultValue = "false") boolean inherited) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReleaseControllerLogConstant.EFFECTIVE_REQUEST,
                id, organizationId);
        List<EffectiveItemResponse> response = releaseQueryService.getEffective(organizationId, id, versionId, inherited);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReleaseControllerLogConstant.EFFECTIVE_COMPLETED,
                response.size(), id, organizationId);
        return ResponseUtil.ok(ReleaseControllerResponseConstant.RESPONSE_EFFECTIVE_RETRIEVED, response);
    }
}
