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
import com.korur.oba.apps.otak.campaign.dto.projection.CampaignContentLanguageProjection;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignQueryRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignDetailResponse;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignListResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
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
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * Implementation of campaign read operations.
 *
 * Every campaign is read within the request's organization, so a campaign of another
 * organization is never listed and reads as not found. The one exception is
 * {@link #getEntityById}, whose caller compares the campaign's organization with the
 * request's itself.
 *
 * The list is not paged. Its owners and the languages of its texts are read once for the
 * whole list and joined in memory by campaign id, so the list costs three queries whatever
 * its length.
 *
 * All methods run within a read-only transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignQueryServiceImpl implements CampaignQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CampaignQueryServiceImpl.class);

    // Fixed orders, with the id as tie-breaker, keep the list stable. Scheduled campaigns read
    // as an agenda, soonest planned first.
    private static final Sort SOONEST_PLANNED_FIRST = Sort.by(Sort.Direction.ASC, CampaignConstant.FIELD_PLANNED_AT)
            .and(Sort.by(Sort.Direction.ASC, CampaignConstant.FIELD_NAME))
            .and(Sort.by(Sort.Direction.ASC, CampaignConstant.FIELD_ID));

    // Every other list shows the campaign changed last first, the one a member works on.
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

        Sort sort = request.getStatus() == CampaignStatus.SCHEDULED ? SOONEST_PLANNED_FIRST : MOST_RECENTLY_CHANGED_FIRST;
        List<Campaign> campaigns = campaignRepository.findAll(
                CampaignSpecification.filterBy(organizationId, request), sort);
        if (campaigns.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<DocumentLanguage>> languages = languagesByCampaign(campaigns);
        Map<UUID, String> ownerNames = campaignOwnerHelper.ownerNames(campaigns);
        List<CampaignListResponse> responses = new ArrayList<>();
        for (Campaign campaign : campaigns) {
            responses.add(campaignMapper.toListResponse(
                    campaign,
                    campaignOwnerHelper.ownerOf(campaign, ownerNames),
                    languages.getOrDefault(campaign.getId(), List.of())));
        }
        return responses;
    }

    @Override
    public CampaignDetailResponse get(UUID organizationId, UUID campaignId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, READING_CAMPAIGN, campaignId, organizationId);

        Campaign campaign = campaignValidator.ensureCampaignExists(organizationId, campaignId);
        List<CampaignContent> contents = campaignContentRepository.findAllByCampaignIdOrderByLanguageAsc(campaignId);
        List<DocumentLanguage> contentLanguages = new ArrayList<>();
        for (CampaignContent content : contents) {
            contentLanguages.add(content.getLanguage());
        }
        return campaignMapper.toDetailResponse(
                campaign,
                campaignOwnerHelper.ownerOf(campaign),
                contentLanguages,
                campaignMapper.toContentResponses(contents));
    }

    @Override
    public void ensureExists(UUID organizationId, UUID campaignId) {
        campaignValidator.ensureCampaignExists(organizationId, campaignId);
    }

    @Override
    public Campaign getEntityById(UUID campaignId) {
        return campaignValidator.ensureCampaignExists(campaignId);
    }

    /**
     * The languages each campaign has a text in, in the order the query returns them (NL, TR,
     * EN), read in one query for the whole list.
     */
    private Map<UUID, List<DocumentLanguage>> languagesByCampaign(List<Campaign> campaigns) {
        List<UUID> campaignIds = new ArrayList<>();
        for (Campaign campaign : campaigns) {
            campaignIds.add(campaign.getId());
        }
        Map<UUID, List<DocumentLanguage>> languages = new HashMap<>();
        for (CampaignContentLanguageProjection row : campaignContentRepository.findLanguages(campaignIds)) {
            List<DocumentLanguage> campaignLanguages = languages.get(row.getCampaignId());
            if (campaignLanguages == null) {
                campaignLanguages = new ArrayList<>();
                languages.put(row.getCampaignId(), campaignLanguages);
            }
            campaignLanguages.add(row.getLanguage());
        }
        return languages;
    }
}
