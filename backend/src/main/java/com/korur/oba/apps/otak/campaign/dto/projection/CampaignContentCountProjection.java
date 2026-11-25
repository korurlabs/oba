package com.korur.oba.apps.otak.campaign.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The number of contents of one campaign, a row of
 * {@link com.korur.oba.apps.otak.campaign.repository.CampaignContentRepository#countByCampaignIds}, read
 * for the whole campaign list in one query.
 */
public interface CampaignContentCountProjection {

    /**
     * @return the campaign id
     */
    UUID getCampaignId();

    /**
     * @return the number of contents of the campaign
     */
    long getContentCount();
}
