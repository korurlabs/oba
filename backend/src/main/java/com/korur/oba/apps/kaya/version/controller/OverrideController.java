package com.korur.oba.apps.kaya.version.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.version.constant.controller.VersionControllerLogConstant;
import com.korur.oba.apps.kaya.version.constant.controller.VersionControllerResponseConstant;
import com.korur.oba.apps.kaya.version.dto.request.ItemOverrideRequest;
import com.korur.oba.apps.kaya.version.dto.response.ItemOverrideResponse;
import com.korur.oba.apps.kaya.version.service.VersionService;

/**
 * The decisions a child pack's draft makes on the items it overrides when a parent changed them:
 * keep the override on the parent's current version, or keep the item as the pack's own once the
 * parent removed it. Dropping an override is deleting the draft's item. Only the draft's proposer
 * writes, as for the draft's files.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/drafts/{id}/overrides")
@Tag(name = "Drafts", description = "Drafts of pack versions, their files and rebase")
public class OverrideController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OverrideController.class);

    private final VersionService versionService;
    private final LoggingService loggingService;

    /**
     * Bases an override of the draft on the parent's current version.
     */
    @PutMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Set the base of an override in a draft",
            description = "Records that the draft's item was reviewed against the current version of the nearest "
                    + "ancestor delivering it. Keep override sends only this; update override saves the file first")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002: not the proposer of the draft, "
                    + "or not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such draft, or the draft holds no item at path"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_014: the version is not a draft; KAY_CFG_032: "
                    + "baseVersionId is not the current version of the nearest ancestor delivering the item, "
                    + "errorValues.parentVersionId names it (null when no ancestor delivers it)"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: path or baseVersionId missing; KAY_CFG_031: "
                    + "an ancestor locks the item"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ItemOverrideResponse>> setOverrideBase(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @Valid @RequestBody ItemOverrideRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.OVERRIDE_SET_REQUEST,
                user.getId(), id, organizationId);
        ItemOverrideResponse response = versionService.setOverrideBase(user, organizationId, id, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.OVERRIDE_SET_COMPLETED,
                response.path(), id, organizationId);
        return ResponseUtil.ok(VersionControllerResponseConstant.RESPONSE_OVERRIDE_SET, response);
    }

    /**
     * Keeps an item whose parent item was removed as the pack's own.
     */
    @DeleteMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Clear an override in a draft",
            description = "Removes the override record of an item no ancestor delivers any more, so the item is the "
                    + "pack's own")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or path missing"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002: not the proposer of the draft"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such draft, or no override of the item"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_014: the version is not a draft"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: path blank or too long, or an ancestor still "
                    + "delivers the item"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> clearOverride(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @Parameter(description = "The item, or a path inside a skill", example = ".agents/rules/10-backend.md")
            @RequestParam @NotBlank @Size(max = ValidationConstant.PATH_MAX_LENGTH) String path) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.OVERRIDE_CLEAR_REQUEST,
                user.getId(), id, organizationId);
        versionService.clearOverride(user, organizationId, id, path);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.OVERRIDE_CLEAR_COMPLETED,
                id, organizationId);
        return ResponseUtil.ok(VersionControllerResponseConstant.RESPONSE_OVERRIDE_CLEARED);
    }
}
