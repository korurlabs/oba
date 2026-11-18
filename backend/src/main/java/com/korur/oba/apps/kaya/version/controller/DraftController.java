package com.korur.oba.apps.kaya.version.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import com.korur.oba.apps.kaya.version.dto.request.DraftCreateRequest;
import com.korur.oba.apps.kaya.version.dto.request.DraftFileSaveRequest;
import com.korur.oba.apps.kaya.version.dto.request.DraftRebaseRequest;
import com.korur.oba.apps.kaya.version.dto.response.DraftFileSaveResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.apps.kaya.version.service.VersionService;

/**
 * The drafts of the organization named by the X-Organization-ID header: start one from a pack's current
 * version, save and delete its files, rebase it, discard it. Every member may
 * start a draft; only the member who proposed a draft may change, rebase or delete it. Submitting a
 * draft for review belongs to the review domain.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/drafts")
@Tag(name = "Drafts", description = "Drafts of pack versions, their files and rebase")
public class DraftController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DraftController.class);

    private final VersionService versionService;
    private final LoggingService loggingService;

    /**
     * Starts a draft as a copy of the owner's current version.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Start a draft of a pack",
            description = "A copy of the pack's current version, or an empty draft for a pack without versions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Draft created"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such pack in the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: packId missing"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<VersionResponse>> createDraft(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @RequestBody DraftCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.DRAFT_CREATE_REQUEST,
                user.getId(), request.packId(), organizationId);
        VersionResponse response = versionService.createDraft(user, organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.DRAFT_CREATE_COMPLETED,
                response.id(), response.number(), organizationId);
        return ResponseUtil.created(VersionControllerResponseConstant.RESPONSE_DRAFT_CREATED, response);
    }

    /**
     * Adds or replaces one file of a draft.
     */
    @PutMapping("/{id}/files")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Save a file of a draft",
            description = "Adds or replaces the file at the path. Content is UTF_8 text unless encoding is BASE64 and "
                    + "is stored as sent; executable is taken only for a hook or a skill script. The response names the "
                    + "entity the file is stored as, its SHA-256 and size, without the content")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002: not the proposer of the draft, "
                    + "or not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such version in the organization"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_014: the version is not a draft"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: invalid request or content that is not base64; "
                    + "KAY_CFG_027 INVALID_FILE: the file does not parse, or is executable but not a hook or a skill "
                    + "script, with the path and the reason; KAY_CFG_028: no file kind takes the path; KAY_CFG_001: the "
                    + "file breaks a file rule or carries a new override marker; KAY_CFG_031: an ancestor that is "
                    + "required, or whose item is a guardrail, already gives the item, with path and pack; KAY_CFG_034: "
                    + "a rule of a pack mounted in a folder lacks paths under that folder, with rule, folder and pack. "
                    + "The draft is unchanged"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DraftFileSaveResponse>> saveFile(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @Valid @RequestBody DraftFileSaveRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.FILE_SAVE_REQUEST,
                user.getId(), id, organizationId);
        DraftFileSaveResponse response = versionService.saveFile(user, organizationId, id, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.FILE_SAVE_COMPLETED,
                response.path(), id, organizationId);
        return ResponseUtil.ok(VersionControllerResponseConstant.RESPONSE_FILE_SAVED, response);
    }

    /**
     * Removes one file from a draft.
     */
    @DeleteMapping("/{id}/files")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete a file of a draft", description = "Removing a SKILL.md removes the whole skill")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or path missing"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002: not the proposer of the draft"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such version, or no file at this path"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_014: the version is not a draft"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: path blank or longer than 500 characters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteFile(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @Parameter(description = "Path of the file relative to the repository root", example = ".agents/rules/core.md")
            @RequestParam @NotBlank @Size(max = ValidationConstant.PATH_MAX_LENGTH) String path) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.FILE_DELETE_REQUEST,
                user.getId(), id, organizationId);
        versionService.deleteFile(user, organizationId, id, path);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.FILE_DELETE_COMPLETED,
                id, organizationId);
        return ResponseUtil.ok(VersionControllerResponseConstant.RESPONSE_FILE_DELETED);
    }

    /**
     * Discards a draft with its files.
     */
    @DeleteMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete a draft", description = "Only a version in DRAFT that no reviewer decided on, by its proposer")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002: not the proposer of the draft"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such version in the organization"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_014: the version is not a draft; "
                    + "KAY_CFG_020: a reviewer decided on it"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteDraft(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.DRAFT_DELETE_REQUEST,
                user.getId(), id, organizationId);
        versionService.deleteDraft(user, organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.DRAFT_DELETE_COMPLETED,
                id, organizationId);
        return ResponseUtil.ok(VersionControllerResponseConstant.RESPONSE_DRAFT_DELETED);
    }

    /**
     * Moves a draft onto the owner's current version.
     */
    @PostMapping("/{id}/rebase")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Rebase a draft on the current version",
            description = "Takes the current version's change of every path the draft left alone. A path both changed "
                    + "is a conflict, resolved by naming it in keepDraft or takeCurrent; the body is optional")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation, or nothing to rebase"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002: not the proposer of the draft"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such version in the organization"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_002: unresolved conflicts, "
                    + "errorValues.conflicts lists them; KAY_CFG_014: the version is not a draft"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: a path in both keepDraft and takeCurrent"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<VersionResponse>> rebase(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) DraftRebaseRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.REBASE_REQUEST,
                user.getId(), id, organizationId);
        VersionResponse response = versionService.rebase(user, organizationId, id, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, VersionControllerLogConstant.REBASE_COMPLETED,
                id, response.baseVersionId(), organizationId);
        return ResponseUtil.ok(VersionControllerResponseConstant.RESPONSE_DRAFT_REBASED, response);
    }
}
