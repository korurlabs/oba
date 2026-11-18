package com.korur.oba.apps.kaizen.pullrequest.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.pullrequest.constant.controller.PullRequestShareControllerLogConstant;
import com.korur.oba.apps.kaizen.pullrequest.constant.controller.PullRequestShareControllerResponseConstant;
import com.korur.oba.apps.kaizen.pullrequest.dto.request.PullRequestShareCreateRequest;
import com.korur.oba.apps.kaizen.pullrequest.dto.request.PullRequestShareQueryRequest;
import com.korur.oba.apps.kaizen.pullrequest.dto.request.PullRequestShareUpdateRequest;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestSharePreviewResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kaizen.pullrequest.service.PullRequestShareQueryService;
import com.korur.oba.apps.kaizen.pullrequest.service.PullRequestShareService;
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
 * REST controller for pull request shares: the cards in an organization's
 * pull requests channel that show a GitHub pull request with the team's
 * status, note, reviewers and approvals.
 *
 * Every call needs the X-Organization-ID header and the MEMBER role, or a
 * higher one, in that organization, and acts on shares of that organization
 * only; a share of another organization is reported as not found. Only the
 * author edits or deletes a share, and only its reviewers approve it or
 * withdraw an approval.
 *
 * Request limits (note length, reviewer count, a null reviewer ID) are
 * checked by Bean Validation and answer the generic validation error, as the
 * daily update endpoints do; reviewer rules that need the organization's
 * members (duplicate, the author, not an active owner, admin or member)
 * answer VAL_PULL_REQUEST_SHARE_INVALID.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaizen/pull-request-shares")
@Tag(name = "Pull Request Shares", description = "Pull request share APIs of the pull requests chat channel")
public class PullRequestShareController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PullRequestShareController.class);

    private final PullRequestShareService pullRequestShareService;
    private final PullRequestShareQueryService pullRequestShareQueryService;
    private final LoggingService loggingService;

    /**
     * Shares a pull request and posts its card in the pull requests channel.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Share pull request",
            description = "Reads the pull request from GitHub and posts its card in the organization's pull "
                    + "requests channel. Title, branches, GitHub author and ticket key come from GitHub; status, "
                    + "note and reviewers from the request. The same pull request may be shared again")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header or "
                    + "malformed body"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled, or "
                    + "the caller may not write in the pull requests channel"),
            @ApiResponse(responseCode = "404", description = "Organization not found or caller is not a member, "
                    + "a related ticket is deleted or on a board the caller is not a member of (KAI_NOT_004), "
                    + "GitHub is not connected for this organization (KAI_NOT_016), the repository is not one of "
                    + "the installation (KAI_NOT_017), or GitHub has no such pull request (KAI_NOT_018)"),
            @ApiResponse(responseCode = "422", description = "Missing field, note over 500 characters, more than "
                    + "10 reviewers or a null reviewer id, more than 10 related tickets or a null related ticket "
                    + "id (GEN_VAL_001); a duplicate reviewer, the author as reviewer, a reviewer who is not an "
                    + "active OWNER, ADMIN or MEMBER, or a duplicate related ticket (KAI_VAL_055); no pull "
                    + "requests channel in the organization (KAI_VAL_056)"),
            @ApiResponse(responseCode = "500", description = "Internal server error"),
            @ApiResponse(responseCode = "502", description = "GitHub unreachable (GHB_UPS_001) or refused the App's "
                    + "credentials (GHB_UPS_002)"),
            @ApiResponse(responseCode = "503", description = "GitHub rate limit reached (GHB_UPS_003)")
    })
    public ResponseEntity<CustomResponse<PullRequestShareResponse>> createShare(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid PullRequestShareCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_CREATE_REQUEST);

        PullRequestShareResponse response = pullRequestShareService.createShare(user, organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_CREATE_COMPLETED);

        return ResponseUtil.created(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_CREATED,
                response);
    }

    /**
     * Previews a new share without saving it.
     */
    @PostMapping("/preview")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Preview pull request share",
            description = "Runs every check of a share, in the same order, reads the pull request from GitHub and "
                    + "returns it with the text its card would show. Saves nothing and posts nothing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header or "
                    + "malformed body"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Organization not found or caller is not a member, "
                    + "a related ticket is deleted or on a board the caller is not a member of (KAI_NOT_004), "
                    + "GitHub is not connected for this organization (KAI_NOT_016), the repository is not one of "
                    + "the installation (KAI_NOT_017), or GitHub has no such pull request (KAI_NOT_018)"),
            @ApiResponse(responseCode = "422", description = "Missing field, note over 500 characters, more than "
                    + "10 reviewers or a null reviewer id, more than 10 related tickets or a null related ticket "
                    + "id (GEN_VAL_001); a duplicate reviewer, the author as reviewer, a reviewer who is not an "
                    + "active OWNER, ADMIN or MEMBER, or a duplicate related ticket (KAI_VAL_055); no pull "
                    + "requests channel in the organization (KAI_VAL_056)"),
            @ApiResponse(responseCode = "500", description = "Internal server error"),
            @ApiResponse(responseCode = "502", description = "GitHub unreachable (GHB_UPS_001) or refused the App's "
                    + "credentials (GHB_UPS_002)"),
            @ApiResponse(responseCode = "503", description = "GitHub rate limit reached (GHB_UPS_003)")
    })
    public ResponseEntity<CustomResponse<PullRequestSharePreviewResponse>> previewShare(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid PullRequestShareCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_PREVIEW_REQUEST);

        PullRequestSharePreviewResponse response = pullRequestShareQueryService.previewShare(user, organizationId,
                request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_PREVIEW_COMPLETED);

        return ResponseUtil.ok(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_PREVIEWED,
                response);
    }

    /**
     * Lists the organization's shares, newest first.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List pull request shares",
            description = "The organization's shares, newest share first; an edit does not move a share. sortBy "
                    + "and sortDirection are ignored. awaitingMyReview=true keeps the shares where the caller is a "
                    + "reviewer who has not approved and the status is not MERGED. channelId and messageId locate "
                    + "each share's card in the chat and are null when the card is gone")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Organization not found or caller is not a member"),
            @ApiResponse(responseCode = "422", description = "Page below 1, amount outside 1 to 50, or an unknown "
                    + "status or malformed author id (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<PullRequestShareResponse>>> listShares(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject PullRequestShareQueryRequest query) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_LIST_REQUEST);

        Page<PullRequestShareResponse> response = pullRequestShareQueryService.listShares(user, organizationId,
                query);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_LIST_COMPLETED);

        return ResponseUtil.ok(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_LIST_RETRIEVED,
                response);
    }

    /**
     * Retrieves one share of the organization.
     */
    @GetMapping("/{shareId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Get pull request share",
            description = "linkedTicket is set only when the caller can open the ticket of ticketKey")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header or "
                    + "share id"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Organization not found or caller is not a member, or "
                    + "the share is absent, deleted or of another organization (KAI_NOT_019)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PullRequestShareResponse>> getShare(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID shareId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_GET_REQUEST);

        PullRequestShareResponse response = pullRequestShareQueryService.getShare(user, organizationId, shareId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_GET_COMPLETED);

        return ResponseUtil.ok(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_RETRIEVED,
                response);
    }

    /**
     * Replaces the status, note and reviewers of the caller's own share.
     */
    @PutMapping("/{shareId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Edit pull request share",
            description = "Full replacement of status, note and reviewers; the pull request itself cannot change "
                    + "and GitHub is not read again. A kept reviewer keeps their approval, an added one starts "
                    + "unapproved. Marks the share and its card edited")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header, share "
                    + "id or body"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled, or "
                    + "the caller is not the author (KAI_SEC_009)"),
            @ApiResponse(responseCode = "404", description = "Organization not found or caller is not a member, "
                    + "the share is absent, deleted or of another organization (KAI_NOT_019), or an added related "
                    + "ticket is deleted or on a board the caller is not a member of (KAI_NOT_004); a related "
                    + "ticket the share already holds is kept without this check"),
            @ApiResponse(responseCode = "422", description = "Missing field, note over 500 characters, more than "
                    + "10 reviewers or a null reviewer id, more than 10 related tickets or a null related ticket "
                    + "id (GEN_VAL_001); a duplicate reviewer, the author as reviewer, a reviewer who is not an "
                    + "active OWNER, ADMIN or MEMBER, or a duplicate related ticket (KAI_VAL_055)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PullRequestShareResponse>> editShare(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID shareId,
            @RequestBody @Valid PullRequestShareUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_EDIT_REQUEST);

        PullRequestShareResponse response = pullRequestShareService.editShare(user, organizationId, shareId,
                request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_EDIT_COMPLETED);

        return ResponseUtil.ok(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_EDITED,
                response);
    }

    /**
     * Soft-deletes the caller's own share and removes its card.
     */
    @DeleteMapping("/{shareId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete pull request share",
            description = "Removes the share and its card from the pull requests channel")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header or "
                    + "share id"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled, or "
                    + "the caller is not the author (KAI_SEC_009)"),
            @ApiResponse(responseCode = "404", description = "Organization not found or caller is not a member, or "
                    + "the share is absent, deleted or of another organization (KAI_NOT_019)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteShare(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID shareId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_DELETE_REQUEST);

        pullRequestShareService.deleteShare(user, organizationId, shareId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_DELETE_COMPLETED);

        return ResponseUtil.ok(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_DELETED);
    }

    /**
     * Approves the share as one of its reviewers.
     */
    @PutMapping("/{shareId}/approval")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Approve pull request share",
            description = "At any status. Approving an approved share changes nothing and answers 200")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header or "
                    + "share id"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled, or "
                    + "the caller is not a reviewer (KAI_SEC_010)"),
            @ApiResponse(responseCode = "404", description = "Organization not found or caller is not a member, or "
                    + "the share is absent, deleted or of another organization (KAI_NOT_019)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PullRequestShareResponse>> approveShare(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID shareId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_APPROVE_REQUEST);

        PullRequestShareResponse response = pullRequestShareService.setApproval(user, organizationId, shareId, true);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_APPROVE_COMPLETED);

        return ResponseUtil.ok(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_APPROVED,
                response);
    }

    /**
     * Withdraws the caller's approval of the share.
     */
    @DeleteMapping("/{shareId}/approval")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Withdraw pull request share approval",
            description = "At any status. Withdrawing an absent approval changes nothing and answers 200")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header or "
                    + "share id"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled, or "
                    + "the caller is not a reviewer (KAI_SEC_010)"),
            @ApiResponse(responseCode = "404", description = "Organization not found or caller is not a member, or "
                    + "the share is absent, deleted or of another organization (KAI_NOT_019)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PullRequestShareResponse>> withdrawShareApproval(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID shareId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_WITHDRAW_REQUEST);

        PullRequestShareResponse response = pullRequestShareService.setApproval(user, organizationId, shareId,
                false);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                PullRequestShareControllerLogConstant.PULL_REQUEST_SHARE_WITHDRAW_COMPLETED);

        return ResponseUtil.ok(
                PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_APPROVAL_WITHDRAWN, response);
    }
}
