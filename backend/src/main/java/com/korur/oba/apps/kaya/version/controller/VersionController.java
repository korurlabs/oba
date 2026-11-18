package com.korur.oba.apps.kaya.version.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.version.constant.controller.VersionControllerLogConstant;
import com.korur.oba.apps.kaya.version.constant.controller.VersionControllerResponseConstant;
import com.korur.oba.apps.kaya.version.dto.request.VersionQueryRequest;
import com.korur.oba.apps.kaya.version.dto.response.FileDiffResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionDetailResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionFileContentResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;
import com.korur.oba.apps.kaya.version.service.VersionService;

/**
 * The versions of the packs of the organization named by the X-Organization-ID header:
 * the history, one version with its rendered files, one file, the diff of two versions, and the
 * restore of an earlier released version as a new draft. Every member may use them.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/versions")
@Tag(name = "Versions", description = "Numbered versions of packs, their files, diffs and restores")
public class VersionController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(VersionController.class);

    private final VersionService versionService;
    private final VersionQueryService versionQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the versions of the organization, newest number first.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List versions", description = "Newest number first, optionally of one pack or one status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: invalid paging"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<VersionResponse>>> getVersions(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject VersionQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.VERSION_LIST_REQUEST,
                organizationId, request);
        Page<VersionResponse> response = versionQueryService.getVersions(organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.VERSION_LIST_COMPLETED,
                response.getNumberOfElements(), organizationId);
        return ResponseUtil.ok(VersionControllerResponseConstant.RESPONSE_VERSIONS_RETRIEVED, response);
    }

    /**
     * Reads one version with its rendered file list.
     */
    @GetMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a version", description = "With its pack and its rendered files by path, without content")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such version in the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<VersionDetailResponse>> getVersion(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.VERSION_GET_REQUEST, id, organizationId);
        VersionDetailResponse response = versionQueryService.getVersion(organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.VERSION_GET_COMPLETED,
                id, response.files().size(), organizationId);
        return ResponseUtil.ok(VersionControllerResponseConstant.RESPONSE_VERSION_RETRIEVED, response);
    }

    /**
     * Reads one rendered file of a version with its content.
     */
    @GetMapping("/{id}/file")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a file of a version",
            description = "Text content as UTF_8, content that is not valid UTF-8 as BASE64; a SYMLINK has its target and no content")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or path missing"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such version in the organization, "
                    + "or the version has no file at this path"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: path blank or longer than 500 characters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<VersionFileContentResponse>> getFile(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @Parameter(description = "Path of the file relative to the repository root", example = ".agents/rules/core.md")
            @RequestParam @NotBlank @Size(max = ValidationConstant.PATH_MAX_LENGTH) String path) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.FILE_GET_REQUEST, id, organizationId);
        VersionFileContentResponse response = versionQueryService.getFileContent(organizationId, id, path);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.FILE_GET_COMPLETED,
                response.path(), id, organizationId);
        return ResponseUtil.ok(VersionControllerResponseConstant.RESPONSE_FILE_RETRIEVED, response);
    }

    /**
     * The file changes of one version against another.
     */
    @GetMapping("/{id}/diff")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Diff a version",
            description = "The changed paths by path, each with a unified diff of its text; against defaults to the "
                    + "version's base, and path limits the list to that one path, empty when it is unchanged. A link or "
                    + "binary content has no unified diff")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such version in the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: path empty or longer than 500 characters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<FileDiffResponse>>> getDiff(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @Parameter(description = "The version to compare with; the version's base when omitted")
            @RequestParam(required = false) UUID against,
            @Parameter(description = "Only this path, relative to the repository root; every changed path when omitted",
                    example = ".agents/rules/core.md")
            @RequestParam(required = false) @Size(min = 1, max = ValidationConstant.PATH_MAX_LENGTH) String path) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.DIFF_REQUEST, id, against, path,
                organizationId);
        List<FileDiffResponse> response = versionQueryService.getDiff(organizationId, id, against, path);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.DIFF_COMPLETED,
                response.size(), id, organizationId);
        return ResponseUtil.ok(VersionControllerResponseConstant.RESPONSE_DIFF_RETRIEVED, response);
    }

    /**
     * Starts a rollback draft with the files of an earlier released version.
     */
    @PostMapping("/{id}/restore")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Restore an earlier released version",
            description = "Creates a draft with source ROLLBACK, based on the current version, whose files are those of "
                    + "the APPROVED or SUPERSEDED version named. It goes through review like any draft")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Rollback draft created"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such version in the organization"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_018: the version was never released; "
                    + "KAY_CFG_016: it is the current version"),
            @ApiResponse(responseCode = "422", description = "KAY_CFG_027 or KAY_CFG_028: a file of the version no longer parses"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<VersionResponse>> restore(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.RESTORE_REQUEST,
                user.getId(), id, organizationId);
        VersionResponse response = versionService.restore(user, organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.RESTORE_COMPLETED,
                response.id(), response.number(), organizationId);
        return ResponseUtil.created(VersionControllerResponseConstant.RESPONSE_ROLLBACK_CREATED, response);
    }
}
