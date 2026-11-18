package com.korur.oba.apps.kaya.settings.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.apps.kaya.settings.constant.controller.SettingsControllerLogConstant;
import com.korur.oba.apps.kaya.settings.constant.controller.SettingsControllerResponseConstant;
import com.korur.oba.apps.kaya.settings.dto.request.SettingsQueryRequest;
import com.korur.oba.apps.kaya.settings.dto.response.SettingsResponse;
import com.korur.oba.apps.kaya.settings.service.SettingsQueryService;

/**
 * The settings files of the versions of the organization named by the X-Organization-ID header, with
 * their permission rules and hooks, read by every member through {@link SettingsQueryService}; settings
 * are written only by parsing a version's files.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/settings")
@Tag(name = "Settings", description = "The .claude/settings.json of pack and project versions")
public class SettingsController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SettingsController.class);

    private final SettingsQueryService settingsQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the settings of the given versions with their rules and hooks.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the settings of versions",
            description = "Not paged; one entry per version that has a settings file, ordered by version. "
                    + "A version id of another organization matches nothing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a version id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the blob of a listed settings file is missing"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: no version id or more than 20"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<SettingsResponse>>> getSettingsList(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject SettingsQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SettingsControllerLogConstant.SETTINGS_LIST_REQUEST,
                request.getVersionId().size(), organizationId);
        List<SettingsResponse> response = settingsQueryService.getSettingsList(organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SettingsControllerLogConstant.SETTINGS_LIST_COMPLETED,
                response.size(), organizationId);
        return ResponseUtil.ok(SettingsControllerResponseConstant.RESPONSE_SETTINGS_LIST_RETRIEVED, response);
    }

    /**
     * Reads one settings file with its rules and hooks.
     */
    @GetMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a settings file", description = "The permission rules and hooks parsed from the file")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID or the id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the organization has no such settings"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SettingsResponse>> getSettings(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SettingsControllerLogConstant.SETTINGS_GET_REQUEST, id, organizationId);
        SettingsResponse response = settingsQueryService.getSettings(organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SettingsControllerLogConstant.SETTINGS_GET_COMPLETED, id, organizationId);
        return ResponseUtil.ok(SettingsControllerResponseConstant.RESPONSE_SETTINGS_RETRIEVED, response);
    }
}
