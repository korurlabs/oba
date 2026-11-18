package com.korur.oba.apps.kaya.agentsfile.controller;

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
import com.korur.oba.apps.kaya.agentsfile.constant.controller.AgentsFileControllerLogConstant;
import com.korur.oba.apps.kaya.agentsfile.constant.controller.AgentsFileControllerResponseConstant;
import com.korur.oba.apps.kaya.agentsfile.dto.request.AgentsFileQueryRequest;
import com.korur.oba.apps.kaya.agentsfile.dto.response.AgentsFileDetailResponse;
import com.korur.oba.apps.kaya.agentsfile.dto.response.AgentsFileListResponse;
import com.korur.oba.apps.kaya.agentsfile.service.AgentsFileQueryService;

/**
 * The agents files of the versions of the organization named by the X-Organization-ID header, read by every
 * member through {@link AgentsFileQueryService}; agents files are written only by parsing a version's files.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/agents-files")
@Tag(name = "Agents files", description = "AGENTS.md files of pack and project versions")
public class AgentsFileController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AgentsFileController.class);

    private final AgentsFileQueryService agentsFileQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the agents files of the given versions.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the agents files of versions",
            description = "Not paged; ordered by version, placement and id. A version id of another organization matches nothing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a version id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: no version id or more than 20"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<AgentsFileListResponse>>> getAgentsFiles(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject AgentsFileQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, AgentsFileControllerLogConstant.AGENTS_FILE_LIST_REQUEST,
                request.getVersionId().size(), organizationId);
        List<AgentsFileListResponse> response = agentsFileQueryService.getAgentsFiles(organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, AgentsFileControllerLogConstant.AGENTS_FILE_LIST_COMPLETED,
                response.size(), organizationId);
        return ResponseUtil.ok(AgentsFileControllerResponseConstant.RESPONSE_AGENTS_FILES_RETRIEVED, response);
    }

    /**
     * Reads one agents file with its content.
     */
    @GetMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read an agents file", description = "The index fields and the whole file as UTF-8 text")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID or the id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the organization has no such agents file"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AgentsFileDetailResponse>> getAgentsFile(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, AgentsFileControllerLogConstant.AGENTS_FILE_GET_REQUEST, id, organizationId);
        AgentsFileDetailResponse response = agentsFileQueryService.getAgentsFile(organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, AgentsFileControllerLogConstant.AGENTS_FILE_GET_COMPLETED, id, organizationId);
        return ResponseUtil.ok(AgentsFileControllerResponseConstant.RESPONSE_AGENTS_FILE_RETRIEVED, response);
    }
}
