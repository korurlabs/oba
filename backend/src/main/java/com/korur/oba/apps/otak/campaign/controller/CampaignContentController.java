package com.korur.oba.apps.otak.campaign.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.controller.CampaignContentControllerLogConstant;
import com.korur.oba.apps.otak.campaign.constant.controller.CampaignContentControllerResponseConstant;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignContentRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignContentResponse;
import com.korur.oba.apps.otak.campaign.service.CampaignContentQueryService;
import com.korur.oba.apps.otak.campaign.service.CampaignContentService;
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
 * REST controller for the contents of a campaign, under /otak/campaigns/{campaignId}/contents:
 * each one thing actually shared, for example one LinkedIn post in one language, published
 * by one legal entity of the organization. Members post by hand and mark a content published.
 *
 * Access: every endpoint needs {@link OrganizationMemberRole#MEMBER} or higher in the
 * organization named by the X-Organization-ID header, checked by
 * {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} through
 * {@link RequiredUserAccess} before the method runs; deleting a content needs no more than
 * changing it. The header's organization is passed to the services, which scope every read
 * and write to it, so a campaign or content of another organization answers not found.
 *
 * The controller holds no rule of its own: Bean Validation checks the request bodies
 * (@Valid, 422 GEN_VAL_001), reads go to {@link CampaignContentQueryService} and writes to
 * {@link CampaignContentService}, and the result is wrapped by {@link ResponseUtil} in a
 * {@link CustomResponse}. Each call is traced with {@link LoggingService#observe} before and
 * after the service call.
 *
 * Clients: the Otak web app and the oba CLI (oba otak campaign content).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/otak/campaigns/{campaignId}/contents")
@Tag(name = "Otak campaign contents", description = "Contents of a campaign: posts and mailings per channel and language")
public class CampaignContentController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CampaignContentController.class);

    private final CampaignContentService campaignContentService;
    private final CampaignContentQueryService campaignContentQueryService;
    private final LoggingService loggingService;

    /**
     * GET /otak/campaigns/{campaignId}/contents: lists every content of a campaign, unpaged,
     * the soonest planned first, the ones without a planned moment last, then the most
     * recently changed.
     *
     * @param user           the authenticated user; the access check has already run, the
     *                       method itself does not read it
     * @param organizationId the organization from the X-Organization-ID header
     * @param campaignId     the campaign identifier
     * @return 200 with the contents, empty when the campaign has none
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CAMPAIGN_FOUND}
     *         (OTK_NOT_012, 404) when the organization has no campaign with the id
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List campaign contents", description = "Every content of one campaign of the "
            + "organization, not paged: the soonest planned first, the ones without a planned moment last, then the "
            + "most recently changed.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Campaign not found or of another organization "
                    + "(OTK_NOT_012)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<CampaignContentResponse>>> listContents(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID campaignId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CampaignContentControllerLogConstant.CAMPAIGN_CONTENT_LIST_REQUEST);

        List<CampaignContentResponse> response = campaignContentQueryService.list(organizationId, campaignId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CampaignContentControllerLogConstant.CAMPAIGN_CONTENT_LIST_COMPLETED, response.size());

        return ResponseUtil.ok(CampaignContentControllerResponseConstant.RESPONSE_CAMPAIGN_CONTENT_LIST_RETRIEVED,
                response);
    }

    /**
     * POST /otak/campaigns/{campaignId}/contents: adds a content to a campaign. The status
     * defaults to DRAFT; PUBLISHED without a published moment stores the server's now.
     *
     * @param user           the authenticated user; the access check has already run, the
     *                       method itself does not read it
     * @param organizationId the organization from the X-Organization-ID header
     * @param campaignId     the campaign identifier
     * @param request        the content payload
     * @return 201 with the created content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CAMPAIGN_FOUND}
     *         (OTK_NOT_012, 404) or LGE_NOT_001 (404) for a legal entity of another organization
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CAMPAIGN_CONTENT_STATUS_DATES}
     *         (OTK_VAL_016, 422)
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create campaign content", description = "Adds a content to one campaign of the "
            + "organization: a legal entity, channel, language and text. Status defaults to DRAFT; PUBLISHED without "
            + "publishedAt stores the server's now.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Campaign (OTK_NOT_012) or legal entity (LGE_NOT_001) "
                    + "not found or of another organization"),
            @ApiResponse(responseCode = "422", description = "Moments that do not fit the status or a published "
                    + "moment in the future (OTK_VAL_016), or invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<CampaignContentResponse>> createContent(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID campaignId,
            @RequestBody @Valid CampaignContentRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CampaignContentControllerLogConstant.CAMPAIGN_CONTENT_CREATE_REQUEST);

        CampaignContentResponse response = campaignContentService.create(organizationId, campaignId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CampaignContentControllerLogConstant.CAMPAIGN_CONTENT_CREATE_COMPLETED);

        return ResponseUtil.created(CampaignContentControllerResponseConstant.RESPONSE_CAMPAIGN_CONTENT_CREATED,
                response);
    }

    /**
     * PUT /otak/campaigns/{campaignId}/contents/{contentId}: replaces every field of a content.
     * A full replace: null clears an optional field and the status is required.
     *
     * @param user           the authenticated user; the access check has already run, the
     *                       method itself does not read it
     * @param organizationId the organization from the X-Organization-ID header
     * @param campaignId     the campaign identifier
     * @param contentId      the content identifier
     * @param request        the content payload
     * @return 200 with the updated content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CAMPAIGN_FOUND}
     *         (OTK_NOT_012, 404),
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CAMPAIGN_CONTENT_FOUND}
     *         (OTK_NOT_013, 404) or LGE_NOT_001 (404)
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CAMPAIGN_CONTENT_STATUS_DATES}
     *         (OTK_VAL_016, 422) or GEN_VAL_001 (422) without a status
     */
    @PutMapping("/{contentId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update campaign content", description = "Replaces every field of a content of one "
            + "campaign of the organization; null clears an optional field and the status is required. PUBLISHED "
            + "without publishedAt stores the server's now.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Campaign (OTK_NOT_012) or legal entity (LGE_NOT_001) "
                    + "not found or of another organization; content not found on this campaign (OTK_NOT_013)"),
            @ApiResponse(responseCode = "422", description = "Moments that do not fit the status or a published "
                    + "moment in the future (OTK_VAL_016), or invalid request body or missing status (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<CampaignContentResponse>> updateContent(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID campaignId,
            @PathVariable UUID contentId,
            @RequestBody @Valid CampaignContentRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CampaignContentControllerLogConstant.CAMPAIGN_CONTENT_UPDATE_REQUEST);

        CampaignContentResponse response = campaignContentService.update(organizationId, campaignId, contentId,
                request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CampaignContentControllerLogConstant.CAMPAIGN_CONTENT_UPDATE_COMPLETED);

        return ResponseUtil.ok(CampaignContentControllerResponseConstant.RESPONSE_CAMPAIGN_CONTENT_UPDATED, response);
    }

    /**
     * DELETE /otak/campaigns/{campaignId}/contents/{contentId}: removes a content and its images.
     *
     * @param user           the authenticated user; the access check has already run, the
     *                       method itself does not read it
     * @param organizationId the organization from the X-Organization-ID header
     * @param campaignId     the campaign identifier
     * @param contentId      the content identifier
     * @return 200 without data
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CAMPAIGN_FOUND}
     *         (OTK_NOT_012, 404) or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CAMPAIGN_CONTENT_FOUND}
     *         (OTK_NOT_013, 404)
     */
    @DeleteMapping("/{contentId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete campaign content", description = "Removes a content of one campaign of the "
            + "organization with its images.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Campaign not found or of another organization "
                    + "(OTK_NOT_012); content not found on this campaign (OTK_NOT_013)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteContent(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID campaignId,
            @PathVariable UUID contentId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CampaignContentControllerLogConstant.CAMPAIGN_CONTENT_DELETE_REQUEST);

        campaignContentService.delete(organizationId, campaignId, contentId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CampaignContentControllerLogConstant.CAMPAIGN_CONTENT_DELETE_COMPLETED);

        return ResponseUtil.ok(CampaignContentControllerResponseConstant.RESPONSE_CAMPAIGN_CONTENT_DELETED);
    }
}
