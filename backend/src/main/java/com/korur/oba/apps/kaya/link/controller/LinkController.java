package com.korur.oba.apps.kaya.link.controller;

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
import com.korur.oba.apps.kaya.link.constant.controller.LinkControllerLogConstant;
import com.korur.oba.apps.kaya.link.constant.controller.LinkControllerResponseConstant;
import com.korur.oba.apps.kaya.link.dto.request.DependencyQueryRequest;
import com.korur.oba.apps.kaya.link.dto.request.LinkQueryRequest;
import com.korur.oba.apps.kaya.link.dto.response.DependencyResponse;
import com.korur.oba.apps.kaya.link.dto.response.LinkResponse;
import com.korur.oba.apps.kaya.link.service.LinkQueryService;

/**
 * The links between Markdown files of a version or a repository of the organization named by the
 * X-Organization-ID header, read through {@link LinkQueryService}. Every member reads them.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/links")
@Tag(name = "Links", description = "Links between delivered Markdown files, FOUND or BROKEN")
public class LinkController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LinkController.class);

    private final LinkQueryService linkQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the links of a version, a repository, or a repository with a version in place.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the links of a scope",
            description = "versionId alone: the links within the version's files. repositoryId alone: those of its latest "
                    + "release, empty while it has none. Both: those the repository would deliver with the DRAFT or IN_REVIEW "
                    + "version in place of its owner's current one. Not paged; by source path and line")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or an id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such version or repository in the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: neither id given, or with both a version that is not "
                    + "open or whose owner the repository does not compose"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<LinkResponse>>> getLinks(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject LinkQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LinkControllerLogConstant.LINKS_REQUEST,
                request.getVersionId(), request.getRepositoryId(), organizationId);
        List<LinkResponse> response = linkQueryService.getLinks(organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LinkControllerLogConstant.LINKS_COMPLETED,
                response.size(), request.getVersionId(), request.getRepositoryId(), organizationId);
        return ResponseUtil.ok(LinkControllerResponseConstant.RESPONSE_LINKS_RETRIEVED, response);
    }

    /**
     * Lists what one file of a scope links to and what uses it.
     */
    @GetMapping("/dependencies")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read the dependencies of a file",
            description = "Within the files of the versions named, what the file at path links to (Markdown links, a "
                    + "rule's conventions, a skill's parts), each FOUND or BROKEN, and what uses it "
                    + "(Markdown links to it, rules citing it, skills sharing it). A path two versions "
                    + "deliver is read from the first named; a version id of another organization is left out")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or an id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no version named delivers the path"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: no versionId or more than 20, or path missing or "
                    + "longer than 500 characters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DependencyResponse>> getDependencies(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject DependencyQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LinkControllerLogConstant.DEPENDENCIES_REQUEST,
                request.getPath(), request.getVersionId(), organizationId);
        DependencyResponse response = linkQueryService.getDependencies(organizationId, request.getVersionId(), request.getPath());
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, LinkControllerLogConstant.DEPENDENCIES_COMPLETED,
                response.linksTo().size(), response.usedBy().size(), request.getPath(), organizationId);
        return ResponseUtil.ok(LinkControllerResponseConstant.RESPONSE_DEPENDENCIES_RETRIEVED, response);
    }
}
