package com.korur.oba.apps.otak.campaign.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.campaign.constant.service.CampaignServiceLogConstant.*;

import com.korur.oba.apps.otak.campaign.dto.request.CampaignCreateRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignUpdateRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignDetailResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.apps.otak.campaign.helper.CampaignOwnerHelper;
import com.korur.oba.apps.otak.campaign.mapper.CampaignMapper;
import com.korur.oba.apps.otak.campaign.repository.CampaignContentRepository;
import com.korur.oba.apps.otak.campaign.repository.CampaignRepository;
import com.korur.oba.apps.otak.campaign.service.CampaignService;
import com.korur.oba.apps.otak.campaign.validator.CampaignValidator;
import com.korur.oba.apps.otak.event.service.EventQueryService;
import com.korur.oba.apps.otak.icp.service.IcpQueryService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

/**
 * Implements writes to an organization's campaigns.
 *
 * The organization role is checked once per request by the access interceptor. Every
 * campaign is read and written within the request's organization, so a campaign of another
 * organization is a missing one. The event and the ICP are checked through their own
 * domains, so a campaign never links to a record its organization cannot see. The contents
 * of a campaign are written by {@link CampaignContentServiceImpl}.
 */
@Service
@RequiredArgsConstructor
public class CampaignServiceImpl implements CampaignService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CampaignServiceImpl.class);

    private final CampaignRepository campaignRepository;
    private final CampaignContentRepository campaignContentRepository;
    private final CampaignMapper campaignMapper;
    private final CampaignValidator campaignValidator;
    private final CampaignOwnerHelper campaignOwnerHelper;
    private final EventQueryService eventQueryService;
    private final IcpQueryService icpQueryService;
    private final AttachmentService attachmentService;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public CampaignDetailResponse create(UUID organizationId, User user, CampaignCreateRequest request) {
        String name = request.name().trim();
        campaignValidator.ensureDateRange(request.startDate(), request.endDate());
        campaignValidator.ensureNameFree(organizationId, name, null);
        campaignValidator.ensureOwnerIsOrganizationMember(organizationId, request.ownerUserId());
        ensureEventOfOrganization(organizationId, request.eventId());
        ensureIcpOfOrganization(organizationId, request.icpId());

        Campaign campaign = campaignMapper.toEntity(request);
        // Stored trimmed, so the stored name is the one the name rule checked.
        campaign.setName(name);
        campaign.setOrganizationId(organizationId);
        campaign = campaignRepository.save(campaign);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CAMPAIGN_CREATED, campaign.getId(), organizationId, user.getId());

        return campaignMapper.toDetailResponse(campaign, campaignOwnerHelper.ownerOf(campaign), 0L);
    }

    @Override
    @Transactional
    public CampaignDetailResponse update(UUID organizationId, UUID campaignId, CampaignUpdateRequest request) {
        Campaign campaign = campaignValidator.ensureCampaignExists(organizationId, campaignId);

        String name = request.name().trim();
        campaignValidator.ensureDateRange(request.startDate(), request.endDate());
        campaignValidator.ensureNameFree(organizationId, name, campaignId);
        // Only a new owner is checked: an owner who left the organization stays on the
        // campaign until someone picks another, and does not block editing the rest.
        if (!Objects.equals(campaign.getOwnerUserId(), request.ownerUserId())) {
            campaignValidator.ensureOwnerIsOrganizationMember(organizationId, request.ownerUserId());
        }
        ensureEventOfOrganization(organizationId, request.eventId());
        ensureIcpOfOrganization(organizationId, request.icpId());

        campaignMapper.updateEntity(campaign, request);
        campaign.setName(name);
        // Flushed here so the response carries the new last-change time.
        campaign = campaignRepository.saveAndFlush(campaign);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CAMPAIGN_UPDATED, campaignId, organizationId);

        return campaignMapper.toDetailResponse(
                campaign,
                campaignOwnerHelper.ownerOf(campaign),
                campaignContentRepository.countByCampaignId(campaignId));
    }

    /**
     * Deletes the campaign and then the images of its contents.
     *
     * The database removes the contents with the campaign row and clears the campaign on its
     * outreaches and relations, but an attachment row names its parent only by type and id,
     * without a foreign key, so the images are removed here. The content ids are read before
     * the delete, and the campaign row is flushed first so that a database failure stops the
     * delete before any attachment row is touched. The attachment rows go in this transaction;
     * storage then deletes, best effort, each blob that no remaining row holds.
     *
     * @param organizationId the organization that owns the campaign
     * @param campaignId     the campaign identifier
     */
    @Override
    @Transactional
    public void delete(UUID organizationId, UUID campaignId) {
        Campaign campaign = campaignValidator.ensureCampaignExists(organizationId, campaignId);
        List<UUID> contentIds = campaignContentRepository.findIdsByCampaignId(campaignId);

        campaignRepository.delete(campaign);
        campaignRepository.flush();

        for (UUID contentId : contentIds) {
            attachmentService.deleteAttachmentsByEntity(StorageEntityType.CAMPAIGN_CONTENT, contentId);
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CAMPAIGN_DELETED, campaignId, organizationId);
    }

    /**
     * Refuses an event that is unknown or belongs to another organization, so a campaign
     * never links to an event its organization cannot see. No event is always allowed.
     */
    private void ensureEventOfOrganization(UUID organizationId, UUID eventId) {
        if (eventId != null) {
            eventQueryService.ensureExists(organizationId, eventId);
        }
    }

    /**
     * Refuses an ICP that is unknown or belongs to another organization, so a campaign never
     * links to a segment its organization cannot see. No ICP is always allowed.
     */
    private void ensureIcpOfOrganization(UUID organizationId, UUID icpId) {
        if (icpId != null) {
            icpQueryService.ensureExists(organizationId, icpId);
        }
    }
}
