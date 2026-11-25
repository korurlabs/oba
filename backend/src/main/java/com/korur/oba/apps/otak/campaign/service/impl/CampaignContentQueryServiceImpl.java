package com.korur.oba.apps.otak.campaign.service.impl;

// 1. Framework-level
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
import static com.korur.oba.apps.otak.campaign.constant.service.CampaignContentServiceLogConstant.*;

import com.korur.oba.apps.otak.campaign.dto.response.CampaignContentResponse;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;
import com.korur.oba.apps.otak.campaign.mapper.CampaignContentMapper;
import com.korur.oba.apps.otak.campaign.repository.CampaignContentRepository;
import com.korur.oba.apps.otak.campaign.service.CampaignContentQueryService;
import com.korur.oba.apps.otak.campaign.validator.CampaignContentValidator;
import com.korur.oba.apps.otak.campaign.validator.CampaignValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.legalentity.entity.LegalEntity;
import com.korur.oba.shrine.legalentity.service.LegalEntityQueryService;

/**
 * Implementation of campaign content read operations.
 *
 * The contents are read through their campaign within the request's organization, so a
 * campaign of another organization reads as not found. The one exception is
 * {@link #getEntityById}, whose caller compares the content's organization with the
 * request's itself.
 *
 * The short name of each legal entity is read once per distinct legal entity of the list,
 * which is a handful, through the legal entity domain.
 *
 * All methods run within a read-only transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignContentQueryServiceImpl implements CampaignContentQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CampaignContentQueryServiceImpl.class);

    private final CampaignContentRepository campaignContentRepository;
    private final CampaignContentMapper campaignContentMapper;
    private final CampaignContentValidator campaignContentValidator;
    private final CampaignValidator campaignValidator;
    private final LegalEntityQueryService legalEntityQueryService;
    private final LoggingService loggingService;

    @Override
    public List<CampaignContentResponse> list(UUID organizationId, UUID campaignId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_CAMPAIGN_CONTENTS, campaignId, organizationId);

        campaignValidator.ensureCampaignExists(organizationId, campaignId);
        List<CampaignContent> contents = campaignContentRepository.findAgenda(campaignId, organizationId);

        Map<UUID, String> shortNames = new HashMap<>();
        List<CampaignContentResponse> responses = new ArrayList<>();
        for (CampaignContent content : contents) {
            UUID legalEntityId = content.getLegalEntityId();
            String shortName = shortNames.get(legalEntityId);
            if (shortName == null) {
                LegalEntity legalEntity = legalEntityQueryService.getEntity(organizationId, legalEntityId);
                shortName = legalEntity.getShortName();
                shortNames.put(legalEntityId, shortName);
            }
            responses.add(campaignContentMapper.toResponse(content, shortName));
        }
        return responses;
    }

    @Override
    public CampaignContent getEntityById(UUID contentId) {
        return campaignContentValidator.ensureContentExists(contentId);
    }

    @Override
    public boolean existsByLegalEntity(UUID organizationId, UUID legalEntityId) {
        return campaignContentRepository.existsByOrganizationIdAndLegalEntityId(organizationId, legalEntityId);
    }
}
