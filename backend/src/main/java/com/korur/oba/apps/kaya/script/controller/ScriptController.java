package com.korur.oba.apps.kaya.script.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.korur.oba.apps.kaya.script.constant.controller.ScriptControllerLogConstant;
import com.korur.oba.apps.kaya.script.constant.controller.ScriptControllerResponseConstant;
import com.korur.oba.apps.kaya.script.dto.response.ScriptDetailResponse;
import com.korur.oba.apps.kaya.script.service.ScriptQueryService;

/**
 * The scripts of the versions of the organization named by the X-Organization-ID header, read by every
 * member through {@link ScriptQueryService}; scripts are listed only as parts of a skill and written only by
 * parsing a version's files.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/scripts")
@Tag(name = "Scripts", description = "Script parts of the skills of pack and project versions")
public class ScriptController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ScriptController.class);

    private final ScriptQueryService scriptQueryService;
    private final LoggingService loggingService;

    /**
     * Reads one script with its content.
     */
    @GetMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a script", description = "The index fields and the whole file as UTF-8 text")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID or the id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the organization has no such script"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ScriptDetailResponse>> getScript(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ScriptControllerLogConstant.SCRIPT_GET_REQUEST, id, organizationId);
        ScriptDetailResponse response = scriptQueryService.getScript(organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ScriptControllerLogConstant.SCRIPT_GET_COMPLETED, id, organizationId);
        return ResponseUtil.ok(ScriptControllerResponseConstant.RESPONSE_SCRIPT_RETRIEVED, response);
    }
}
