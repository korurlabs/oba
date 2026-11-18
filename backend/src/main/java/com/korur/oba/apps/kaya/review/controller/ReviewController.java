package com.korur.oba.apps.kaya.review.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
import com.korur.oba.apps.kaya.review.constant.controller.ReviewControllerLogConstant;
import com.korur.oba.apps.kaya.review.constant.controller.ReviewControllerResponseConstant;
import com.korur.oba.apps.kaya.review.dto.request.ChangeQueryRequest;
import com.korur.oba.apps.kaya.review.dto.request.ChangeSubmitRequest;
import com.korur.oba.apps.kaya.review.dto.request.FileHistoryQueryRequest;
import com.korur.oba.apps.kaya.review.dto.request.ReviewRequest;
import com.korur.oba.apps.kaya.review.dto.response.ChangeDetailResponse;
import com.korur.oba.apps.kaya.review.dto.response.ChangeResponse;
import com.korur.oba.apps.kaya.review.dto.response.FileHistoryResponse;
import com.korur.oba.apps.kaya.review.dto.response.ReviewResultResponse;
import com.korur.oba.apps.kaya.review.service.ReviewQueryService;
import com.korur.oba.apps.kaya.review.service.ReviewService;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;

/**
 * The changes of the organization named by the X-Organization-ID header: submitting a draft for
 * review, the review decisions through {@link ReviewService}, and the Changes list and review
 * screen through {@link ReviewQueryService}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/changes")
@Tag(name = "Changes", description = "Submitting drafts, reviewing versions and the changes list")
public class ReviewController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ReviewController.class);

    private final ReviewService reviewService;
    private final ReviewQueryService reviewQueryService;
    private final LoggingService loggingService;

    /**
     * Submits a draft for review.
     */
    @PostMapping("/{versionId}/submit")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Submit a draft for review",
            description = "The draft must rest on its owner's current version and change it, and every repository that "
                    + "composes the owner must stay valid with it; a pack "
                    + "has at most one version in review")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a malformed body"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002: not the proposer of the draft"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such version in the organization"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_002: rebase needed; KAY_CFG_003: a path from two "
                    + "owners; KAY_CFG_014: not a draft; KAY_CFG_015: another version in review; KAY_CFG_016: no change"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: reason shorter than 10 or longer than 1000 "
                    + "characters; KAY_CFG_001: a composition breaks a rule, errorValues.violations lists them"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<VersionResponse>> submit(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID versionId,
            @Valid @RequestBody ChangeSubmitRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReviewControllerLogConstant.SUBMIT_REQUEST,
                user.getId(), versionId, organizationId);
        VersionResponse response = reviewService.submit(user, organizationId, versionId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReviewControllerLogConstant.SUBMIT_COMPLETED,
                versionId, organizationId);
        return ResponseUtil.ok(ReviewControllerResponseConstant.RESPONSE_DRAFT_SUBMITTED, response);
    }

    /**
     * Approves, rejects or comments on a version in review.
     */
    @PostMapping("/{versionId}/reviews")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Review a version",
            description = "APPROVE releases the version to every repository that composes its pack, as signed "
                    + "releases; REJECT sends it back to its proposer as a draft; COMMENT leaves it in review. The "
                    + "proposer never reviews; an executable change needs an OWNER to approve")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Review recorded; on APPROVE with the new releases"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a malformed body"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Below ADMIN; KAY_CFG_004: the proposer, or an ADMIN "
                    + "approving an executable change; errorValues.reason is PROPOSER or ROLE"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such version in the organization"),
            @ApiResponse(responseCode = "409", description = "KAY_CFG_017: the version is not in review; KAY_CFG_002: "
                    + "the owner moved on since the submit; KAY_CFG_003: a path from two owners of a repository"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: no decision, or a REJECT or COMMENT without a "
                    + "comment; KAY_CFG_001: a composition breaks a rule, errorValues.violations lists them"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ReviewResultResponse>> review(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID versionId,
            @Valid @RequestBody ReviewRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReviewControllerLogConstant.REVIEW_REQUEST,
                user.getId(), versionId, request.decision(), organizationId);
        ReviewResultResponse response = reviewService.review(user, organizationId, versionId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReviewControllerLogConstant.REVIEW_COMPLETED,
                versionId, response.version().status(), response.releases().size(), organizationId);
        return ResponseUtil.ok(ReviewControllerResponseConstant.RESPONSE_REVIEW_RECORDED, response);
    }

    /**
     * Lists the changes of the organization.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List changes",
            description = "Every version of the organization's packs with the role its approval needs and "
                    + "its reviews; most recently changed version first")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a filter value malformed"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: page or amount out of range"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<ChangeResponse>>> getChanges(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject ChangeQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReviewControllerLogConstant.CHANGES_REQUEST,
                organizationId, request.getPackId(), request.getStatus());
        Page<ChangeResponse> response = reviewQueryService.getChanges(organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReviewControllerLogConstant.CHANGES_COMPLETED,
                response.getNumberOfElements(), organizationId);
        return ResponseUtil.ok(ReviewControllerResponseConstant.RESPONSE_CHANGES_RETRIEVED, response);
    }

    /**
     * Lists the versions that changed one file, with their reviews.
     */
    @GetMapping("/history")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read the history of a file",
            description = "The versions of the pack of versionId that added, changed or deleted the file at "
                    + "path against their base, newest number first, each with its reviews. Released versions, the "
                    + "version in review and the caller's own drafts are read; other members' drafts are not. A page "
                    + "reads amount versions and lists those that changed the file, so it may hold fewer entries; its "
                    + "totals count versions read")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or versionId not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such version in the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: versionId or path missing, path longer than 500 "
                    + "characters, or page or amount out of range"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<FileHistoryResponse>>> getFileHistory(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject FileHistoryQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReviewControllerLogConstant.HISTORY_REQUEST,
                user.getId(), request.getPath(), request.getVersionId(), organizationId);
        Page<FileHistoryResponse> response = reviewQueryService.getFileHistory(user, organizationId,
                request.getVersionId(), request.getPath(), request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReviewControllerLogConstant.HISTORY_COMPLETED,
                response.getNumberOfElements(), request.getPath(), organizationId);
        return ResponseUtil.ok(ReviewControllerResponseConstant.RESPONSE_HISTORY_RETRIEVED, response);
    }

    /**
     * Reads one change for its review screen.
     */
    @GetMapping("/{versionId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a change",
            description = "The change with its reviews; for a version in review also the rules an approval would refuse "
                    + "it for now and whether the caller may approve, reject or comment; for the caller's own draft the "
                    + "rules a submit would refuse it for")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such version in the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChangeDetailResponse>> getChange(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID versionId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReviewControllerLogConstant.CHANGE_REQUEST,
                user.getId(), versionId, organizationId);
        ChangeDetailResponse response = reviewQueryService.getChange(user, organizationId, versionId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReviewControllerLogConstant.CHANGE_COMPLETED,
                versionId, response.reviews().size(), organizationId);
        return ResponseUtil.ok(ReviewControllerResponseConstant.RESPONSE_CHANGE_RETRIEVED, response);
    }
}
