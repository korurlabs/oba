package com.korur.oba.apps.otak.campaign.controller;

// 1. Framework-level
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
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;

import jakarta.validation.Valid;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.controller.CampaignControllerLogConstant;
import com.korur.oba.apps.otak.campaign.constant.controller.CampaignControllerResponseConstant;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignCreateRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignQueryRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignUpdateRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignDetailResponse;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignListResponse;
import com.korur.oba.apps.otak.campaign.service.CampaignQueryService;
import com.korur.oba.apps.otak.campaign.service.CampaignService;
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
 * REST controller for the campaigns of an organization: the umbrellas over the posts and
 * mailings that belong together. What is actually shared lives in the contents of a campaign,
 * served by {@link CampaignContentController}. Every member reads and changes campaigns; only
 * an admin deletes one.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/otak/campaigns")
@Tag(name = "Otak campaigns", description = "Campaigns of an organization, the umbrellas of its posts and mailings")
public class CampaignController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CampaignController.class);

    private final CampaignService campaignService;
    private final CampaignQueryService campaignQueryService;
    private final LoggingService loggingService;

    /**
     * Lists every campaign that matches the filters, unpaged.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List campaigns",
            description = "Every campaign of the organization that matches the optional event and name search, "
                    + "not paged, the most recently changed first, each with the number of its contents.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "422", description = "Invalid event id or search (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<CampaignListResponse>>> listCampaigns(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject CampaignQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, CampaignControllerLogConstant.CAMPAIGN_LIST_REQUEST);

        List<CampaignListResponse> response = campaignQueryService.list(organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, CampaignControllerLogConstant.CAMPAIGN_LIST_COMPLETED,
                response.size());

        return ResponseUtil.ok(CampaignControllerResponseConstant.RESPONSE_CAMPAIGN_LIST_RETRIEVED, response);
    }

    /**
     * Creates a campaign without contents.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create campaign",
            description = "Creates a campaign of the organization; its contents are added through the nested "
                    + "contents endpoints.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Event (OTK_NOT_009) or ICP (OTK_NOT_004) not found or "
                    + "of another organization"),
            @ApiResponse(responseCode = "409", description = "Another campaign of the organization has this name, case "
                    + "ignored (OTK_STA_015), or the owner is no active member (OTK_STA_001)"),
            @ApiResponse(responseCode = "422", description = "End date before start date (OTK_VAL_017), owner "
                    + "without Otak access (APP_VAL_002), or invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<CampaignDetailResponse>> createCampaign(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid CampaignCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, CampaignControllerLogConstant.CAMPAIGN_CREATE_REQUEST);

        CampaignDetailResponse response = campaignService.create(organizationId, user, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, CampaignControllerLogConstant.CAMPAIGN_CREATE_COMPLETED);

        return ResponseUtil.created(CampaignControllerResponseConstant.RESPONSE_CAMPAIGN_CREATED, response);
    }

    /**
     * Reads one campaign with the number of its contents.
     */
    @GetMapping("/{campaignId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Get campaign", description = "Every field of one campaign of the organization with the number of its contents.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Campaign not found or of another organization "
                    + "(OTK_NOT_012)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<CampaignDetailResponse>> getCampaign(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID campaignId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, CampaignControllerLogConstant.CAMPAIGN_GET_REQUEST);

        CampaignDetailResponse response = campaignQueryService.get(organizationId, campaignId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, CampaignControllerLogConstant.CAMPAIGN_GET_COMPLETED);

        return ResponseUtil.ok(CampaignControllerResponseConstant.RESPONSE_CAMPAIGN_RETRIEVED, response);
    }

    /**
     * Replaces every field of a campaign; its contents stay.
     */
    @PutMapping("/{campaignId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update campaign",
            description = "Replaces every field of a campaign: send the whole campaign, since null or a left-out "
                    + "optional field clears it. The contents stay as they are.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Campaign (OTK_NOT_012), event (OTK_NOT_009) or ICP "
                    + "(OTK_NOT_004) not found or of another organization"),
            @ApiResponse(responseCode = "409", description = "Another campaign of the organization has this name, case "
                    + "ignored (OTK_STA_015), or a new owner is no active member (OTK_STA_001)"),
            @ApiResponse(responseCode = "422", description = "End date before start date (OTK_VAL_017), owner "
                    + "without Otak access (APP_VAL_002), or invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<CampaignDetailResponse>> updateCampaign(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID campaignId,
            @RequestBody @Valid CampaignUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, CampaignControllerLogConstant.CAMPAIGN_UPDATE_REQUEST);

        CampaignDetailResponse response = campaignService.update(organizationId, campaignId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, CampaignControllerLogConstant.CAMPAIGN_UPDATE_COMPLETED);

        return ResponseUtil.ok(CampaignControllerResponseConstant.RESPONSE_CAMPAIGN_UPDATED, response);
    }

    /**
     * Deletes a campaign with its contents and their images; its outreaches and relations stay
     * and lose the link.
     */
    @DeleteMapping("/{campaignId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete campaign",
            description = "Removes a campaign with its contents and their images; the outreaches and relations "
                    + "linked to it stay and lose the link. Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Campaign not found or of another organization "
                    + "(OTK_NOT_012)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteCampaign(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID campaignId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, CampaignControllerLogConstant.CAMPAIGN_DELETE_REQUEST);

        campaignService.delete(organizationId, campaignId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, CampaignControllerLogConstant.CAMPAIGN_DELETE_COMPLETED);

        return ResponseUtil.ok(CampaignControllerResponseConstant.RESPONSE_CAMPAIGN_DELETED);
    }
}
