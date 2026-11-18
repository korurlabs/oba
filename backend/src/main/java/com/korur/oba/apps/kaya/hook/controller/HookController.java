package com.korur.oba.apps.kaya.hook.controller;

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
import com.korur.oba.apps.kaya.hook.constant.controller.HookControllerLogConstant;
import com.korur.oba.apps.kaya.hook.constant.controller.HookControllerResponseConstant;
import com.korur.oba.apps.kaya.hook.dto.request.HookQueryRequest;
import com.korur.oba.apps.kaya.hook.dto.response.HookDetailResponse;
import com.korur.oba.apps.kaya.hook.dto.response.HookListResponse;
import com.korur.oba.apps.kaya.hook.service.HookQueryService;

/**
 * The hooks of the versions of the organization named by the X-Organization-ID header, read by every
 * member through {@link HookQueryService}; hooks are written only by parsing a version's files.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/hooks")
@Tag(name = "Hooks", description = "Hook scripts of pack and project versions")
public class HookController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(HookController.class);

    private final HookQueryService hookQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the hooks of the given versions.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the hooks of versions",
            description = "Not paged; ordered by version, path and id. A version id of another organization matches nothing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a version id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: no version id or more than 20"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<HookListResponse>>> getHooks(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject HookQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, HookControllerLogConstant.HOOK_LIST_REQUEST,
                request.getVersionId().size(), organizationId);
        List<HookListResponse> response = hookQueryService.getHooks(organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, HookControllerLogConstant.HOOK_LIST_COMPLETED,
                response.size(), organizationId);
        return ResponseUtil.ok(HookControllerResponseConstant.RESPONSE_HOOKS_RETRIEVED, response);
    }

    /**
     * Reads one hook with its content.
     */
    @GetMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a hook", description = "The index fields and the whole file as UTF-8 text")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID or the id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the organization has no such hook"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<HookDetailResponse>> getHook(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, HookControllerLogConstant.HOOK_GET_REQUEST, id, organizationId);
        HookDetailResponse response = hookQueryService.getHook(organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, HookControllerLogConstant.HOOK_GET_COMPLETED, id, organizationId);
        return ResponseUtil.ok(HookControllerResponseConstant.RESPONSE_HOOK_RETRIEVED, response);
    }
}
