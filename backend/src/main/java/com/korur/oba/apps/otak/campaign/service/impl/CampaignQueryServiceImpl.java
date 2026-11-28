package com.korur.oba.apps.otak.campaign.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.campaign.constant.service.CampaignServiceLogConstant.*;

import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.apps.otak.campaign.dto.projection.CampaignContentCountProjection;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignQueryRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignDetailResponse;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignListResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.apps.otak.campaign.helper.CampaignOwnerHelper;
import com.korur.oba.apps.otak.campaign.mapper.CampaignMapper;
import com.korur.oba.apps.otak.campaign.repository.CampaignContentRepository;
import com.korur.oba.apps.otak.campaign.repository.CampaignRepository;
import com.korur.oba.apps.otak.campaign.service.CampaignQueryService;
import com.korur.oba.apps.otak.campaign.specification.CampaignSpecification;
import com.korur.oba.apps.otak.campaign.validator.CampaignValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Implementation of campaign read operations.
 *
 * Every campaign is read within the request's organization, so a campaign of another
 * organization is never listed and reads as not found.
 *
 * The list is not paged. Its owners and the number of contents per campaign are read once for
 * the whole list and joined in memory by campaign id, so the list costs three queries whatever
 * its length.
 *
 * All methods run within a read-only transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignQueryServiceImpl implements CampaignQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CampaignQueryServiceImpl.class);

    // The campaign changed last comes first, the one a member works on; the id keeps the order stable.
    private static final Sort MOST_RECENTLY_CHANGED_FIRST = Sort.by(Sort.Direction.DESC, CampaignConstant.FIELD_UPDATED_AT)
            .and(Sort.by(Sort.Direction.DESC, CampaignConstant.FIELD_ID));

    private final CampaignRepository campaignRepository;
    private final CampaignContentRepository campaignContentRepository;
    private final CampaignMapper campaignMapper;
    private final CampaignValidator campaignValidator;
    private final CampaignOwnerHelper campaignOwnerHelper;
    private final LoggingService loggingService;

    @Override
    public List<CampaignListResponse> list(UUID organizationId, CampaignQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_CAMPAIGNS, organizationId, request);

        List<Campaign> campaigns = campaignRepository.findAll(
                CampaignSpecification.filterBy(organizationId, request), MOST_RECENTLY_CHANGED_FIRST);
        if (campaigns.isEmpty()) {
            return List.of();
        }

        Map<UUID, Long> contentCounts = contentCountsByCampaign(campaigns);
        Map<UUID, String> ownerNames = campaignOwnerHelper.ownerNames(campaigns);
        List<CampaignListResponse> responses = new ArrayList<>();
        for (Campaign campaign : campaigns) {
            Long contentCount = contentCounts.get(campaign.getId());
            responses.add(campaignMapper.toListResponse(
                    campaign,
                    campaignOwnerHelper.ownerOf(campaign, ownerNames),
                    contentCount == null ? 0L : contentCount));
        }
        return responses;
    }

    @Override
    public CampaignDetailResponse get(UUID organizationId, UUID campaignId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, READING_CAMPAIGN, campaignId, organizationId);

        Campaign campaign = campaignValidator.ensureCampaignExists(organizationId, campaignId);
        return campaignMapper.toDetailResponse(
                campaign,
                campaignOwnerHelper.ownerOf(campaign),
                campaignContentRepository.countByCampaignId(campaignId));
    }

    @Override
    public void ensureExists(UUID organizationId, UUID campaignId) {
        campaignValidator.ensureCampaignExists(organizationId, campaignId);
    }

    @Override
    public Campaign getEntity(UUID organizationId, UUID campaignId) {
        return campaignValidator.ensureCampaignExists(organizationId, campaignId);
    }

    /**
     * The number of contents per campaign, read in one grouped query for the whole list. A
     * campaign without contents has no entry.
     */
    private Map<UUID, Long> contentCountsByCampaign(List<Campaign> campaigns) {
        List<UUID> campaignIds = new ArrayList<>();
        for (Campaign campaign : campaigns) {
            campaignIds.add(campaign.getId());
        }
        Map<UUID, Long> contentCounts = new HashMap<>();
        for (CampaignContentCountProjection row : campaignContentRepository.countByCampaignIds(campaignIds)) {
            contentCounts.put(row.getCampaignId(), row.getContentCount());
        }
        return contentCounts;
    }
}
