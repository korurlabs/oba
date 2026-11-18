package com.korur.oba.apps.kaya.release.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
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
import com.korur.oba.apps.kaya.release.constant.controller.ReleaseControllerLogConstant;
import com.korur.oba.apps.kaya.release.constant.controller.ReleaseControllerResponseConstant;
import com.korur.oba.apps.kaya.release.dto.request.ReleaseQueryRequest;
import com.korur.oba.apps.kaya.release.dto.response.ReleaseDetailResponse;
import com.korur.oba.apps.kaya.release.dto.response.ReleaseResponse;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;

/**
 * The signed releases of a repository of the organization named by the X-Organization-ID header,
 * read through {@link ReleaseQueryService}. Every member reads them; releases are created only by
 * an approval or a pack order change.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/repositories/{id}/releases")
@Tag(name = "Releases", description = "Signed releases of a repository")
public class ReleaseController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ReleaseController.class);

    private final ReleaseQueryService releaseQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the releases of a repository.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the releases of a repository", description = "Paged, newest number first")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "No such repository in the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: page or amount out of range"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<ReleaseResponse>>> getReleases(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @Valid @ModelAttribute @ParameterObject ReleaseQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReleaseControllerLogConstant.RELEASE_LIST_REQUEST, id, organizationId);
        Page<ReleaseResponse> response = releaseQueryService.getReleases(organizationId, id, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReleaseControllerLogConstant.RELEASE_LIST_COMPLETED,
                response.getNumberOfElements(), id, organizationId);
        return ResponseUtil.ok(ReleaseControllerResponseConstant.RESPONSE_RELEASES_RETRIEVED, response);
    }

    /**
     * Reads one release with its signature and delivered paths.
     */
    @GetMapping("/{number}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a release of a repository", description = "With the signature and every delivered path")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such repository, or no release with that number"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ReleaseDetailResponse>> getRelease(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @PathVariable int number) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReleaseControllerLogConstant.RELEASE_GET_REQUEST,
                number, id, organizationId);
        ReleaseDetailResponse response = releaseQueryService.getRelease(organizationId, id, number);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReleaseControllerLogConstant.RELEASE_GET_COMPLETED,
                number, id, organizationId);
        return ResponseUtil.ok(ReleaseControllerResponseConstant.RESPONSE_RELEASE_RETRIEVED, response);
    }
}
