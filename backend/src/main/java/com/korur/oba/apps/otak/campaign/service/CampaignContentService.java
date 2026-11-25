package com.korur.oba.apps.otak.campaign.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.request.CampaignContentRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignContentResponse;

/**
 * Write service interface for the contents of an organization's campaigns. The caller's role
 * in the organization is checked before these methods run; every method works within that
 * organization and the campaign of the path, so a campaign or content of another
 * organization is a missing one.
 */
public interface CampaignContentService {

    /**
     * Adds a content to a campaign of the organization.
     *
     * @param organizationId the organization that owns the campaign
     * @param campaignId     the campaign identifier
     * @param request        the content payload; a null status means DRAFT
     * @return the created content
     */
    CampaignContentResponse create(UUID organizationId, UUID campaignId, CampaignContentRequest request);

    /**
     * Replaces every field of a content; null clears an optional field and the status is required.
     *
     * @param organizationId the organization that owns the campaign
     * @param campaignId     the campaign identifier
     * @param contentId      the content identifier
     * @param request        the content payload
     * @return the updated content
     */
    CampaignContentResponse update(UUID organizationId, UUID campaignId, UUID contentId,
                                   CampaignContentRequest request);

    /**
     * Deletes a content and its images.
     *
     * @param organizationId the organization that owns the campaign
     * @param campaignId     the campaign identifier
     * @param contentId      the content identifier
     */
    void delete(UUID organizationId, UUID campaignId, UUID contentId);
}
