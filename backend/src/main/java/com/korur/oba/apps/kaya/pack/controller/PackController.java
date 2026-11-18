package com.korur.oba.apps.kaya.pack.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import com.korur.oba.apps.kaya.pack.constant.controller.PackControllerLogConstant;
import com.korur.oba.apps.kaya.pack.constant.controller.PackControllerResponseConstant;
import com.korur.oba.apps.kaya.pack.dto.request.PackCreateRequest;
import com.korur.oba.apps.kaya.pack.dto.request.PackUpdateRequest;
import com.korur.oba.apps.kaya.pack.dto.response.PackDetailResponse;
import com.korur.oba.apps.kaya.pack.dto.response.PackResponse;
import com.korur.oba.apps.kaya.pack.service.PackService;
import com.korur.oba.apps.kaya.release.service.RepositoryPackQueryService;

/**
 * The packs of the organization named by the X-Organization-ID header: every member lists them,
 * with the folders each is mounted in, and reads one with the repositories that compose it, through
 * {@link RepositoryPackQueryService}, which holds the mounts; only an OWNER creates or updates one
 * through {@link PackService}, and renames only one that nothing uses yet.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/packs")
@Tag(name = "Packs", description = "Named sets of shared agent config of the organization")
public class PackController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PackController.class);

    private final PackService packService;
    private final RepositoryPackQueryService repositoryPackQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the packs of the organization.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the packs of the organization", description = "Not paged; ordered by name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<PackResponse>>> getPacks(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PackControllerLogConstant.PACK_LIST_REQUEST, organizationId);
        List<PackResponse> response = repositoryPackQueryService.getPacksWithMountPaths(organizationId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PackControllerLogConstant.PACK_LIST_COMPLETED,
                response.size(), organizationId);
        return ResponseUtil.ok(PackControllerResponseConstant.RESPONSE_PACKS_RETRIEVED, response);
    }

    /**
     * Reads one pack of the organization with the repositories that compose it.
     */
    @GetMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a pack with its repositories",
            description = "The pack as the list gives it, with the repositories of the organization that compose it, by "
                    + "name: those that list it, or every active repository when the pack is required. Each repository "
                    + "carries the folder it mounts the pack in, null at its root, and its latest release number")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the organization has no pack of that id"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PackDetailResponse>> getPack(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PackControllerLogConstant.PACK_GET_REQUEST, id, organizationId);
        PackDetailResponse response = repositoryPackQueryService.getPackWithRepositories(organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PackControllerLogConstant.PACK_GET_COMPLETED,
                id, organizationId, response.repositories().size());
        return ResponseUtil.ok(PackControllerResponseConstant.RESPONSE_PACK_RETRIEVED, response);
    }

    /**
     * Creates a root pack or a child pack without versions.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.OWNER)
    @Operation(summary = "Create a pack or a child pack", description = "The pack has no version until one is "
            + "approved. A parentPackId makes it a child pack of that pack; the parent cannot be changed later")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Pack created"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or the body malformed"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not an OWNER of the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the organization has no pack of the parentPackId"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_008: the organization has a pack of that name"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: name not [a-z0-9-]{1,60}, or description "
                    + "blank or over 1000 characters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PackResponse>> createPack(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @RequestBody PackCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PackControllerLogConstant.PACK_CREATE_REQUEST, organizationId);
        PackResponse response = packService.createPack(user, organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PackControllerLogConstant.PACK_CREATE_COMPLETED,
                response.id(), organizationId);
        return ResponseUtil.created(PackControllerResponseConstant.RESPONSE_PACK_CREATED, response);
    }

    /**
     * Updates a pack's name and description; the name changes only while nothing uses the pack.
     */
    @PatchMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.OWNER)
    @Operation(summary = "Update a pack's name and description",
            description = "Both fields are written as given, so a null description removes it. The description changes "
                    + "at any time. The name keys where a mounted pack's files live, seed imports and plan matching, so "
                    + "a new name is accepted only for a pack that is not required, that no repository composes (also "
                    + "not through a pack below it), that has no version other than drafts, no draft file in its "
                    + "name's namespace and no plan. The same name and description as now answer the pack without a "
                    + "change")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, id not a UUID, or the body malformed"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not an OWNER of the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the organization has no pack of that id"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_035: the pack is in use, the condition in errorValues; "
                    + "KAY_CFG_008: the organization has a pack of that name; both only when the name changes"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: name not [a-z0-9-]{1,60}, or description "
                    + "blank or over 1000 characters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PackResponse>> updatePack(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @Valid @RequestBody PackUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PackControllerLogConstant.PACK_UPDATE_REQUEST, id, organizationId);
        PackResponse response = packService.updatePack(user, organizationId, id, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PackControllerLogConstant.PACK_UPDATE_COMPLETED,
                id, organizationId, response.name());
        return ResponseUtil.ok(PackControllerResponseConstant.RESPONSE_PACK_UPDATED, response);
    }
}
