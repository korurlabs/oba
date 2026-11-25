package com.korur.oba.apps.otak.campaign.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.response.CampaignContentResponse;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;

/**
 * Read service interface for the contents of an organization's campaigns.
 */
public interface CampaignContentQueryService {

    /**
     * Lists every content of a campaign of the organization, unpaged: the soonest planned
     * first, the ones without a planned moment last, then the most recently changed.
     *
     * @param organizationId the organization that owns the campaign
     * @param campaignId     the campaign identifier
     * @return the contents, empty when the campaign has none
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException OTK_NOT_012
     *         when the organization has no campaign with the id
     */
    List<CampaignContentResponse> list(UUID organizationId, UUID campaignId);

    /**
     * Reads one content entity by id alone, in any organization, for the access check of an
     * attachment whose parent is a content. The caller must compare the content's
     * organization with the request's before it uses the content.
     *
     * @param contentId the content identifier
     * @return the content entity
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException OTK_NOT_013
     *         when no content has the id
     */
    CampaignContent getEntityById(UUID contentId);
}
