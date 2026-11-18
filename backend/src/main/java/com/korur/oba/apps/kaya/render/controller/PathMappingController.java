package com.korur.oba.apps.kaya.render.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
import com.korur.oba.apps.kaya.render.constant.controller.PathMappingControllerLogConstant;
import com.korur.oba.apps.kaya.render.constant.controller.PathMappingControllerResponseConstant;
import com.korur.oba.apps.kaya.render.dto.request.PathMappingQueryRequest;
import com.korur.oba.apps.kaya.render.dto.response.PathMappingResponse;
import com.korur.oba.apps.kaya.render.dto.response.ToolMappingResponse;
import com.korur.oba.apps.kaya.render.mapper.PathMappingMapper;
import com.korur.oba.apps.kaya.render.service.RenderQueryService;

/**
 * Where every kind lives in a repository, read by every member of the organization named by the
 * X-Organization-ID header. The rows come from {@link RenderQueryService#getPathMappings}, which
 * builds them beside the path rules render delivers by, and are never written. The tool view of
 * {@link RenderQueryService#getToolMappings} shows the same rows from the side of each AI tool.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/path-mappings")
@Tag(name = "Path mappings", description = "The delivery path of every kind, with its file count in a scope")
public class PathMappingController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PathMappingController.class);

    private final RenderQueryService renderQueryService;
    private final PathMappingMapper pathMappingMapper;
    private final LoggingService loggingService;

    /**
     * Lists the path mapping rows with their counts in the given versions.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the path mappings",
            description = "Not paged; one row per agents file placement, kind and skill part, always in the same order. "
                    + "Without a version id the counts are null; an id of another organization or an unknown id counts 0")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a version id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: more than 20 version ids"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<PathMappingResponse>>> getPathMappings(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject PathMappingQueryRequest request) {
        List<UUID> versionIds = request.getVersionId() == null ? List.of() : request.getVersionId();
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PathMappingControllerLogConstant.PATH_MAPPING_LIST_REQUEST,
                versionIds.size(), organizationId);
        List<PathMappingResponse> response = pathMappingMapper.toResponses(
                renderQueryService.getPathMappings(organizationId, versionIds));
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PathMappingControllerLogConstant.PATH_MAPPING_LIST_COMPLETED,
                response.size(), organizationId);
        return ResponseUtil.ok(PathMappingControllerResponseConstant.RESPONSE_PATH_MAPPINGS_RETRIEVED, response);
    }

    /**
     * Lists, per AI tool, the paths it reads with their state in the given versions.
     */
    @GetMapping("/tools")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List what each AI tool reads",
            description = "Not paged; one entry per tool (Claude Code, OpenAI Codex, Cursor, Gemini CLI), each listing the "
                    + "paths it reads, when it loads them, the link it passes and the source row it ends at, and the paths "
                    + "Kaya does not deliver. Without a version id the states and counts are null; an id of another "
                    + "organization or an unknown id counts 0")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a version id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: more than 20 version ids"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<ToolMappingResponse>>> getToolMappings(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject PathMappingQueryRequest request) {
        List<UUID> versionIds = request.getVersionId() == null ? List.of() : request.getVersionId();
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PathMappingControllerLogConstant.TOOL_MAPPING_LIST_REQUEST,
                versionIds.size(), organizationId);
        List<ToolMappingResponse> response = pathMappingMapper.toToolResponses(
                renderQueryService.getToolMappings(organizationId, versionIds));
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PathMappingControllerLogConstant.TOOL_MAPPING_LIST_COMPLETED,
                response.size(), organizationId);
        return ResponseUtil.ok(PathMappingControllerResponseConstant.RESPONSE_TOOL_MAPPINGS_RETRIEVED, response);
    }
}
