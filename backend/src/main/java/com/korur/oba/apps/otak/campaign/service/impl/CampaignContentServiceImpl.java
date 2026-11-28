package com.korur.oba.apps.otak.campaign.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.campaign.constant.service.CampaignContentServiceLogConstant.*;

import com.korur.oba.apps.otak.campaign.dto.request.CampaignContentRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignContentResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.apps.otak.campaign.mapper.CampaignContentMapper;
import com.korur.oba.apps.otak.campaign.repository.CampaignContentRepository;
import com.korur.oba.apps.otak.campaign.service.CampaignContentService;
import com.korur.oba.apps.otak.campaign.validator.CampaignContentValidator;
import com.korur.oba.apps.otak.campaign.validator.CampaignValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.legalentity.entity.LegalEntity;
import com.korur.oba.shrine.legalentity.service.LegalEntityQueryService;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

/**
 * Implements writes to the contents of an organization's campaigns.
 *
 * The organization role is checked once per request by the access interceptor. Each method
 * first reads the campaign of the path within the request's organization, so a campaign of
 * another organization is a missing one, and a content is read within that campaign. The
 * legal entity is checked through its own domain, so a content is never published by a
 * company of another organization. The organization of a content is copied from its
 * campaign, never taken from a body.
 *
 * A content marked PUBLISHED without a published moment gets the server's now from the UTC
 * {@link Clock}; a moment the client sends is kept, so a post that went live earlier can be
 * back-dated.
 */
@Service
@RequiredArgsConstructor
public class CampaignContentServiceImpl implements CampaignContentService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CampaignContentServiceImpl.class);

    private final CampaignContentRepository campaignContentRepository;
    private final CampaignContentMapper campaignContentMapper;
    private final CampaignContentValidator campaignContentValidator;
    private final CampaignValidator campaignValidator;
    private final LegalEntityQueryService legalEntityQueryService;
    private final AttachmentService attachmentService;
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public CampaignContentResponse create(UUID organizationId, UUID campaignId, CampaignContentRequest request) {
        Campaign campaign = campaignValidator.ensureCampaignExists(organizationId, campaignId);
        LegalEntity legalEntity = legalEntityQueryService.getEntity(organizationId, request.legalEntityId());
        CampaignStatus status = request.status() == null ? CampaignStatus.DRAFT : request.status();
        campaignContentValidator.ensureStatusDates(status, request.plannedAt(), request.publishedAt());

        CampaignContent content = campaignContentMapper.toEntity(request);
        content.setCampaignId(campaign.getId());
        content.setOrganizationId(campaign.getOrganizationId());
        content.setStatus(status);
        content.setPublishedAt(publishedAtFor(status, request.publishedAt()));
        // Flushed here so the response carries the creation and last-change time.
        content = campaignContentRepository.saveAndFlush(content);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CAMPAIGN_CONTENT_CREATED, content.getId(), campaignId,
                organizationId);

        return campaignContentMapper.toResponse(content, legalEntity.getShortName());
    }

    @Override
    @Transactional
    public CampaignContentResponse update(UUID organizationId, UUID campaignId, UUID contentId,
                                          CampaignContentRequest request) {
        campaignValidator.ensureCampaignExists(organizationId, campaignId);
        CampaignContent content = campaignContentValidator.ensureContentExists(organizationId, campaignId, contentId);
        campaignContentValidator.ensureStatusGiven(contentId, request.status());
        LegalEntity legalEntity = legalEntityQueryService.getEntity(organizationId, request.legalEntityId());
        campaignContentValidator.ensureStatusDates(request.status(), request.plannedAt(), request.publishedAt());

        campaignContentMapper.updateEntity(content, request);
        content.setStatus(request.status());
        content.setPublishedAt(publishedAtFor(request.status(), request.publishedAt()));
        // Flushed here so the response carries the new last-change time.
        content = campaignContentRepository.saveAndFlush(content);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CAMPAIGN_CONTENT_UPDATED, contentId, campaignId,
                organizationId);

        return campaignContentMapper.toResponse(content, legalEntity.getShortName());
    }

    /**
     * Deletes the content and then its images.
     *
     * An attachment row names its parent only by type and id, without a foreign key, so the
     * images are removed here. The content row is flushed first so that a database failure
     * stops the delete before any attachment row is touched; storage then deletes, best
     * effort, each blob that no remaining row holds.
     *
     * @param organizationId the organization that owns the campaign
     * @param campaignId     the campaign identifier
     * @param contentId      the content identifier
     */
    @Override
    @Transactional
    public void delete(UUID organizationId, UUID campaignId, UUID contentId) {
        campaignValidator.ensureCampaignExists(organizationId, campaignId);
        CampaignContent content = campaignContentValidator.ensureContentExists(organizationId, campaignId, contentId);

        campaignContentRepository.delete(content);
        campaignContentRepository.flush();

        attachmentService.deleteAttachmentsByEntity(StorageEntityType.CAMPAIGN_CONTENT, contentId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CAMPAIGN_CONTENT_DELETED, contentId, campaignId,
                organizationId);
    }

    /**
     * The published moment to store: the server's now for a PUBLISHED content sent without
     * one, otherwise the moment as sent (the validator has already refused one on a DRAFT or
     * SCHEDULED content, and one in the future).
     */
    private Instant publishedAtFor(CampaignStatus status, Instant requestedPublishedAt) {
        if (status == CampaignStatus.PUBLISHED && requestedPublishedAt == null) {
            return Instant.now(clock);
        }
        return requestedPublishedAt;
    }
}
