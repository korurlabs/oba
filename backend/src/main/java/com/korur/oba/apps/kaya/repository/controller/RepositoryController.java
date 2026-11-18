package com.korur.oba.apps.kaya.repository.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.korur.oba.apps.kaya.repository.constant.controller.RepositoryControllerLogConstant;
import com.korur.oba.apps.kaya.repository.constant.controller.RepositoryControllerResponseConstant;
import com.korur.oba.apps.kaya.repository.dto.request.RepositoryUpdateRequest;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.repository.service.RepositoryService;

/**
 * Repositories of the organization named by the X-Organization-ID header. Repositories are
 * connected by ingest when a recorder first reports them; this controller lists them and changes
 * their name, ticket URL template and status.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/repositories")
@Tag(name = "Repositories", description = "Repositories of the organization and their settings")
public class RepositoryController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RepositoryController.class);

    private final RepositoryService repositoryService;
    private final RepositoryQueryService repositoryQueryService;
    private final LoggingService loggingService;

    /**
     * Lists every repository of the organization.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the repositories of the organization",
            description = "Every repository, ARCHIVED ones included, ordered by name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<RepositoryResponse>>> getRepositories(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                RepositoryControllerLogConstant.REPOSITORY_LIST_REQUEST, organizationId);

        List<RepositoryResponse> response = repositoryQueryService.getRepositories(organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                RepositoryControllerLogConstant.REPOSITORY_LIST_COMPLETED, response.size(), organizationId);

        return ResponseUtil.ok(RepositoryControllerResponseConstant.RESPONSE_REPOSITORIES_RETRIEVED, response);
    }

    /**
     * Changes the name, ticket URL template or status of a repository.
     */
    @PatchMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Change the name, ticket URL template or status of a repository",
            description = "A field left out or null keeps its value; an empty ticketUrlTemplate removes it. "
                    + "Ingest for an ARCHIVED repository answers 422 KAY_VAL_002")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Repository updated"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or malformed body"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_003: the caller is not ADMIN"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such repository in the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: a field is invalid"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<RepositoryResponse>> updateRepository(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @Valid @RequestBody RepositoryUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                RepositoryControllerLogConstant.REPOSITORY_UPDATE_REQUEST, id, organizationId);

        RepositoryResponse response = repositoryService.updateRepository(user, organizationId, id, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                RepositoryControllerLogConstant.REPOSITORY_UPDATE_COMPLETED, id, organizationId);

        return ResponseUtil.ok(RepositoryControllerResponseConstant.RESPONSE_REPOSITORY_UPDATED, response);
    }
}
