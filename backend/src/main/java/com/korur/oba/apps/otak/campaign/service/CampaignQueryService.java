package com.korur.oba.apps.otak.campaign.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.request.CampaignQueryRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignDetailResponse;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignListResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;

/**
 * Read service interface for an organization's campaigns.
 */
public interface CampaignQueryService {

    /**
     * Lists every campaign of the organization that matches the filters, unpaged, the most
     * recently changed first, each with the number of its contents.
     *
     * @param organizationId the organization that owns the campaigns
     * @param request        the event and name filters
     * @return every matching campaign
     */
    List<CampaignListResponse> list(UUID organizationId, CampaignQueryRequest request);

    /**
     * Reads one campaign of the organization with the number of its contents.
     *
     * @param organizationId the organization that owns the campaign
     * @param campaignId     the campaign identifier
     * @return the campaign
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException OTK_NOT_012
     *         when the organization has no campaign with the id
     */
    CampaignDetailResponse get(UUID organizationId, UUID campaignId);

    /**
     * Ensures the organization has the campaign, for the outreach and relation domains before
     * they link a record to one. A campaign of another organization is not found.
     *
     * @param organizationId the organization that owns the campaign
     * @param campaignId     the campaign identifier
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException OTK_NOT_012
     *         when the organization has no campaign with the id
     */
    void ensureExists(UUID organizationId, UUID campaignId);

    /**
     * Reads one campaign entity of the organization, for a caller that needs its fields. A
     * campaign of another organization is not found.
     *
     * @param organizationId the organization that owns the campaign
     * @param campaignId     the campaign identifier
     * @return the campaign entity
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException OTK_NOT_012
     *         when the organization has no campaign with the id
     */
    Campaign getEntity(UUID organizationId, UUID campaignId);
}
