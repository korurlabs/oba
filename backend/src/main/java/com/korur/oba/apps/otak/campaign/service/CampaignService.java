package com.korur.oba.apps.otak.campaign.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.request.CampaignCreateRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignUpdateRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignDetailResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write service interface for an organization's campaigns. The caller's role in the
 * organization is checked before these methods run; every method works within that
 * organization, so a campaign of another organization is a missing one.
 */
public interface CampaignService {

    /**
     * Creates a campaign without contents.
     *
     * @param organizationId the organization that owns the campaign
     * @param user           the authenticated user
     * @param request        the campaign payload
     * @return the created campaign
     */
    CampaignDetailResponse create(UUID organizationId, User user, CampaignCreateRequest request);

    /**
     * Replaces every field of a campaign; null clears an optional field. The contents stay.
     *
     * @param organizationId the organization that owns the campaign
     * @param campaignId     the campaign identifier
     * @param request        the campaign payload
     * @return the updated campaign
     */
    CampaignDetailResponse update(UUID organizationId, UUID campaignId, CampaignUpdateRequest request);

    /**
     * Deletes a campaign; the database deletes its contents and clears the campaign link of
     * every outreach and relation that pointed to it, and keeps them. The images of the
     * contents are removed too.
     *
     * @param organizationId the organization that owns the campaign
     * @param campaignId     the campaign identifier
     */
    void delete(UUID organizationId, UUID campaignId);
}
