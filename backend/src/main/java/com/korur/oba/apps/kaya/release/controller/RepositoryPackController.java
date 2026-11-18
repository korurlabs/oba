package com.korur.oba.apps.kaya.release.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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
import com.korur.oba.apps.kaya.release.constant.controller.ReleaseControllerLogConstant;
import com.korur.oba.apps.kaya.release.constant.controller.ReleaseControllerResponseConstant;
import com.korur.oba.apps.kaya.release.dto.request.RepositoryPackUpdateRequest;
import com.korur.oba.apps.kaya.release.dto.response.RepositoryPackResponse;
import com.korur.oba.apps.kaya.release.service.RepositoryPackQueryService;
import com.korur.oba.apps.kaya.release.service.RepositoryPackService;

/**
 * The packs a repository of the organization named by the X-Organization-ID header composes, in
 * position order: every member reads them, only an OWNER replaces them, through
 * {@link RepositoryPackService}, which releases the new composition at once.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/repositories/{id}/packs")
@Tag(name = "Repository packs", description = "The packs a repository composes, in position order")
public class RepositoryPackController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RepositoryPackController.class);

    private final RepositoryPackService repositoryPackService;
    private final RepositoryPackQueryService repositoryPackQueryService;
    private final LoggingService loggingService;

    /**
     * Reads the packs a repository composes.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read the packs of a repository",
            description = "In composition order: the organization's required packs by name, also those the repository does not "
                    + "list, then the repository's own packs by position; with the latest release")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "No such repository in the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<RepositoryPackResponse>> getPacks(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReleaseControllerLogConstant.PACKS_GET_REQUEST, id, organizationId);
        RepositoryPackResponse response = repositoryPackQueryService.getPacks(organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReleaseControllerLogConstant.PACKS_GET_COMPLETED,
                id, organizationId, response.packs().size());
        return ResponseUtil.ok(ReleaseControllerResponseConstant.RESPONSE_PACKS_RETRIEVED, response);
    }

    /**
     * Replaces the packs a repository composes.
     */
    @PutMapping
    @RequiredUserAccess(OrganizationMemberRole.OWNER)
    @Operation(summary = "Replace the packs of a repository",
            description = "The list is the new pack list in display order, each pack at the root or mounted in a folder. "
                    + "It is validated as the repository would deliver it, then stored with a new signed release in one transaction. An unchanged list creates no release; "
                    + "a composition without an approved version yet stores the packs without a release")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or the body malformed"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not an OWNER of the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such repository, or a listed pack the organization "
                    + "does not have"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_003: two composed packs not on one chain give one path; "
                    + "errorValues.violations lists them"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: more than 50 packs, a null pack id or a blank folder; "
                    + "KAY_CFG_001: a pack is listed twice or the composition breaks a rule, such as PACK_MOUNT_INHERITED or "
                    + "MOUNT_PATH_OUTSIDE_NAMESPACE; errorValues.violations lists every violation; KAY_CFG_026: a mount folder is "
                    + "malformed, below .agents, .claude or docs, given twice, or inside or around another; errorValues.rootPath "
                    + "says which"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<RepositoryPackResponse>> replacePacks(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @Valid @RequestBody RepositoryPackUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReleaseControllerLogConstant.PACKS_UPDATE_REQUEST, id, organizationId);
        RepositoryPackResponse response = repositoryPackService.replacePacks(user, organizationId, id, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReleaseControllerLogConstant.PACKS_UPDATE_COMPLETED,
                id, organizationId, response.packs().size());
        return ResponseUtil.ok(ReleaseControllerResponseConstant.RESPONSE_PACKS_UPDATED, response);
    }
}
