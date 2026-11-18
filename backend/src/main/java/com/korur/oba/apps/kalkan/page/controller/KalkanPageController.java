package com.korur.oba.apps.kalkan.page.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.constant.controller.KalkanPageControllerLogConstant;
import com.korur.oba.apps.kalkan.page.constant.controller.KalkanPageControllerResponseConstant;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageCreateRequest;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageMoveRequest;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageUpdateRequest;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageUpsertRequest;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageDetailResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageListResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageUpsertResponse;
import com.korur.oba.apps.kalkan.page.service.KalkanPageQueryService;
import com.korur.oba.apps.kalkan.page.service.KalkanPageService;
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

/**
 * The page tree of the organization named by the X-Organization-ID header. The gate admits every
 * GUEST to the two reads and every MEMBER to the writes; the services then require the caller's
 * level on the page's root, and a page the caller cannot see answers 404.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kalkan/pages")
@Tag(name = "Kalkan pages", description = "The page tree of an organization's docs")
public class KalkanPageController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(KalkanPageController.class);

    private final KalkanPageService kalkanPageService;
    private final KalkanPageQueryService kalkanPageQueryService;
    private final LoggingService loggingService;

    /**
     * Lists every page under the roots the caller can see.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List the page tree",
            description = "Every live page under a root the caller holds a level on, flat, parents before children; "
                    + "roots carry the caller's level")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<KalkanPageListResponse>>> getTree(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_TREE_REQUEST, user.getId(), organizationId);
        List<KalkanPageListResponse> response = kalkanPageQueryService.getTree(user, organizationId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_TREE_COMPLETED, response.size(), organizationId);
        return ResponseUtil.ok(KalkanPageControllerResponseConstant.RESPONSE_PAGES_RETRIEVED, response);
    }

    /**
     * Creates or updates the page at a path, as a push does.
     */
    @PutMapping("/by-path")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create or update a page by its path",
            description = "Creates the page when the path names no live page, else updates it unless it was changed "
                    + "in Kalkan since the last push; needs EDIT on the root, a new root ADMIN. dryRun reports the "
                    + "outcome without writing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "CREATED, UPDATED or UNCHANGED"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a malformed body"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Below MEMBER; KLK_SEC_001: below EDIT on the root, or a "
                    + "new root below ADMIN"),
            @ApiResponse(responseCode = "404", description = "KLK_NOT_001: the root is hidden from the caller"),
            @ApiResponse(responseCode = "409", description = "KLK_STA_004: changed in Kalkan since the last push; "
                    + "GEN_STA_001: changed concurrently"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: invalid path or translation; KLK_VAL_001: "
                    + "a parent path is missing; KLK_VAL_005: a locale twice; KLK_VAL_007: reserved root slug"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<KalkanPageUpsertResponse>> upsertByPath(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestBody @Valid KalkanPageUpsertRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_UPSERT_REQUEST, user.getId(), request.path(), organizationId,
                dryRun);
        KalkanPageUpsertResponse response = kalkanPageService.upsertByPath(user, organizationId, request, dryRun);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_UPSERT_COMPLETED, request.path(), organizationId,
                response.outcome());
        return ResponseUtil.ok(KalkanPageControllerResponseConstant.RESPONSE_PAGE_UPSERTED, response);
    }

    /**
     * Reads a page with its translations.
     */
    @GetMapping("/{pageId}")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Read a page", description = "The page with its translations; needs VIEW on its root")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization"),
            @ApiResponse(responseCode = "404", description = "KLK_NOT_001: no such page, or hidden from the caller"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<KalkanPageDetailResponse>> getPage(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID pageId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_GET_REQUEST, user.getId(), pageId, organizationId);
        KalkanPageDetailResponse response = kalkanPageQueryService.getPage(user, organizationId, pageId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_GET_COMPLETED, pageId, organizationId);
        return ResponseUtil.ok(KalkanPageControllerResponseConstant.RESPONSE_PAGE_RETRIEVED, response);
    }

    /**
     * Creates a page, or a root page when no parent is given.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create a page",
            description = "Needs EDIT on the parent's root; a root page needs ADMIN and gives its creator MANAGE")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Page created"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a malformed body"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Below MEMBER; KLK_SEC_001: below EDIT on the parent's root, "
                    + "or a root below ADMIN"),
            @ApiResponse(responseCode = "404", description = "KLK_NOT_001: no such parent, or hidden from the caller"),
            @ApiResponse(responseCode = "409", description = "KLK_STA_001: slug taken under the parent"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: invalid field; KLK_VAL_004: deeper than 10 "
                    + "levels; KLK_VAL_005: a locale twice; KLK_VAL_007: reserved root slug"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<KalkanPageDetailResponse>> createPage(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid KalkanPageCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_CREATE_REQUEST, user.getId(), request.slug(), request.parentId(),
                organizationId);
        KalkanPageDetailResponse response = kalkanPageService.createPage(user, organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_CREATE_COMPLETED, response.id(), organizationId);
        return ResponseUtil.created(KalkanPageControllerResponseConstant.RESPONSE_PAGE_CREATED, response);
    }

    /**
     * Changes a page's slug, compliance mark and translations.
     */
    @PutMapping("/{pageId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update a page",
            description = "Writes the given fields and locales against the version the editor loaded; needs EDIT")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Page updated"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a malformed body"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Below MEMBER; KLK_SEC_001: below EDIT on the root"),
            @ApiResponse(responseCode = "404", description = "KLK_NOT_001: no such page, or hidden from the caller"),
            @ApiResponse(responseCode = "409", description = "KLK_STA_001: slug taken; KLK_STA_002: a newer version "
                    + "exists; GEN_STA_001: changed concurrently"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: invalid field; KLK_VAL_005: a locale twice; "
                    + "KLK_VAL_007: reserved root slug"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<KalkanPageDetailResponse>> updatePage(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID pageId,
            @RequestBody @Valid KalkanPageUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_UPDATE_REQUEST, user.getId(), pageId, organizationId);
        KalkanPageDetailResponse response = kalkanPageService.updatePage(user, organizationId, pageId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_UPDATE_COMPLETED, pageId, organizationId, response.version());
        return ResponseUtil.ok(KalkanPageControllerResponseConstant.RESPONSE_PAGE_UPDATED, response);
    }

    /**
     * Moves a page under a new parent, or reorders a root page.
     */
    @PatchMapping("/{pageId}/position")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Move a page",
            description = "A new parent and position; needs EDIT on the source and the target root")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Page moved"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a malformed body"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Below MEMBER; KLK_SEC_001: below EDIT on a root"),
            @ApiResponse(responseCode = "404", description = "KLK_NOT_001: no such page or parent, or hidden from the caller"),
            @ApiResponse(responseCode = "409", description = "KLK_STA_001: slug taken under the new parent; KLK_STA_002: "
                    + "a newer version exists; GEN_STA_001: changed concurrently"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: invalid field; KLK_VAL_004: into its own "
                    + "subtree, deeper than 10 levels, a root under a page or a page to the top"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<KalkanPageDetailResponse>> movePage(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID pageId,
            @RequestBody @Valid KalkanPageMoveRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_MOVE_REQUEST, user.getId(), pageId, request.parentId(),
                organizationId);
        KalkanPageDetailResponse response = kalkanPageService.movePage(user, organizationId, pageId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_MOVE_COMPLETED, pageId, organizationId);
        return ResponseUtil.ok(KalkanPageControllerResponseConstant.RESPONSE_PAGE_MOVED, response);
    }

    /**
     * Soft-deletes a page and its subtree.
     */
    @DeleteMapping("/{pageId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete a page",
            description = "Soft-deletes the page and its whole subtree; needs EDIT, a root MANAGE. The pages, their "
                    + "translations, the root's grants and every attachment stay on record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Page deleted"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Below MEMBER; KLK_SEC_001: below EDIT, or a root below MANAGE"),
            @ApiResponse(responseCode = "404", description = "KLK_NOT_001: no such page, or hidden from the caller"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deletePage(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID pageId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_DELETE_REQUEST, user.getId(), pageId, organizationId);
        kalkanPageService.deletePage(user, organizationId, pageId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_DELETE_COMPLETED, pageId, organizationId);
        return ResponseUtil.ok(KalkanPageControllerResponseConstant.RESPONSE_PAGE_DELETED);
    }

    /**
     * Restores a deleted page and the pages its delete took.
     */
    @PostMapping("/{pageId}/restore")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Restore a deleted page",
            description = "Restores the page and exactly the pages its delete took; needs EDIT, a root MANAGE")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Page restored"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Below MEMBER; KLK_SEC_001: below EDIT, or a root below MANAGE"),
            @ApiResponse(responseCode = "404", description = "KLK_NOT_001: no such page, or hidden from the caller"),
            @ApiResponse(responseCode = "409", description = "KLK_STA_001: slug taken meanwhile; KLK_STA_003: the "
                    + "parent is deleted"),
            @ApiResponse(responseCode = "422", description = "KLK_VAL_008: the page is not deleted; KLK_VAL_004: the "
                    + "restored pages would be deeper than 10"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<KalkanPageDetailResponse>> restorePage(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID pageId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_RESTORE_REQUEST, user.getId(), pageId, organizationId);
        KalkanPageDetailResponse response = kalkanPageService.restorePage(user, organizationId, pageId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                KalkanPageControllerLogConstant.PAGE_RESTORE_COMPLETED, pageId, organizationId);
        return ResponseUtil.ok(KalkanPageControllerResponseConstant.RESPONSE_PAGE_RESTORED, response);
    }
}
