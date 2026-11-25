package com.korur.oba.apps.otak.campaign.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.campaign.constant.service.CampaignServiceLogConstant.*;

import com.korur.oba.apps.otak.campaign.dto.request.CampaignContentRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignCreateRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignUpdateRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignDetailResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
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
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

/**
 * Implements writes to an organization's campaigns and their texts.
 *
 * The organization role is checked once per request by the access interceptor. Every
 * campaign is read and written within the request's organization, so a campaign of another
 * organization is a missing one. The event and the ICP are checked through their own
 * domains, so a campaign never links to a record its organization cannot see.
 *
 * A campaign marked PUBLISHED without a published moment gets the server's now from the UTC
 * {@link Clock}; a moment the client sends is kept, so a post that went live earlier can be
 * back-dated.
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
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public CampaignDetailResponse create(UUID organizationId, User user, CampaignCreateRequest request) {
        String name = request.name().trim();
        CampaignStatus status = Objects.requireNonNullElse(request.status(), CampaignStatus.DRAFT);
        campaignValidator.ensureStatusDates(status, request.plannedAt(), request.publishedAt());
        campaignValidator.ensureLanguagesDistinct(request.contents());
        campaignValidator.ensureNameFree(organizationId, name, null);
        campaignValidator.ensureOwnerIsOrganizationMember(organizationId, request.ownerUserId());
        ensureEventOfOrganization(organizationId, request.eventId());
        ensureIcpOfOrganization(organizationId, request.icpId());

        Campaign campaign = campaignMapper.toEntity(request);
        // Stored trimmed, so the stored name is the one the name rule checked.
        campaign.setName(name);
        campaign.setStatus(status);
        campaign.setPublishedAt(publishedAtFor(status, request.publishedAt()));
        campaign.setOrganizationId(organizationId);
        campaign = campaignRepository.save(campaign);

        List<CampaignContent> contents = replaceContents(campaign.getId(), List.of(), request.contents());

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CAMPAIGN_CREATED, campaign.getId(), organizationId, user.getId());

        return toDetailResponse(campaign, contents);
    }

    @Override
    @Transactional
    public CampaignDetailResponse update(UUID organizationId, UUID campaignId, CampaignUpdateRequest request) {
        Campaign campaign = campaignValidator.ensureCampaignExists(organizationId, campaignId);

        String name = request.name().trim();
        campaignValidator.ensureStatusDates(request.status(), request.plannedAt(), request.publishedAt());
        campaignValidator.ensureLanguagesDistinct(request.contents());
        campaignValidator.ensureNameFree(organizationId, name, campaignId);
        // Only a new owner is checked: an owner who left the organization stays on the
        // campaign until someone picks another, and does not block editing the rest.
        if (!Objects.equals(campaign.getOwnerUserId(), request.ownerUserId())) {
            campaignValidator.ensureOwnerIsOrganizationMember(organizationId, request.ownerUserId());
        }
        ensureEventOfOrganization(organizationId, request.eventId());
        ensureIcpOfOrganization(organizationId, request.icpId());

        Instant previousUpdatedAt = campaign.getUpdatedAt();
        campaignMapper.updateEntity(campaign, request);
        campaign.setName(name);
        campaign.setPublishedAt(publishedAtFor(request.status(), request.publishedAt()));
        // Flushed here so the response carries the new last-change time.
        campaign = campaignRepository.saveAndFlush(campaign);
        boolean campaignRowChanged = !Objects.equals(previousUpdatedAt, campaign.getUpdatedAt());

        List<CampaignContent> stored = campaignContentRepository.findAllByCampaignIdOrderByLanguageAsc(campaignId);
        boolean textsChanged = textsChanged(stored, request.contents());
        List<CampaignContent> contents = replaceContents(campaignId, stored, request.contents());

        // A change to the texts alone writes no campaign column, so the campaign's last-change
        // time is moved here; the list sorts by it, and an edited text must bring the campaign up.
        if (textsChanged && !campaignRowChanged) {
            campaignRepository.touchUpdatedAt(campaignId, Instant.now(clock));
            // Read again: the statement bypasses the loaded campaign, which still has the old time.
            campaign = campaignValidator.ensureCampaignExists(organizationId, campaignId);
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CAMPAIGN_UPDATED, campaignId, organizationId);

        return toDetailResponse(campaign, contents);
    }

    /**
     * Deletes the campaign and then the attachments of the campaign.
     *
     * The database removes the campaign's texts with the row and clears the campaign on its
     * outreaches and relations, but an attachment row names its parent only by type and id,
     * without a foreign key, so it is removed here. The campaign row is flushed first so that a
     * database failure stops the delete before any attachment row is touched. The attachment rows
     * go in this transaction; storage then deletes, best effort, each blob that no remaining row
     * holds.
     *
     * @param organizationId the organization that owns the campaign
     * @param campaignId     the campaign identifier
     */
    @Override
    @Transactional
    public void delete(UUID organizationId, UUID campaignId) {
        Campaign campaign = campaignValidator.ensureCampaignExists(organizationId, campaignId);

        campaignRepository.delete(campaign);
        campaignRepository.flush();

        attachmentService.deleteAttachmentsByEntity(StorageEntityType.CAMPAIGN, campaignId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, CAMPAIGN_DELETED, campaignId, organizationId);
    }

    /**
     * The published moment to store: the server's now for a PUBLISHED campaign sent without
     * one, otherwise the moment as sent (the validator has already refused one on a DRAFT or
     * SCHEDULED campaign, and one in the future).
     */
    private Instant publishedAtFor(CampaignStatus status, Instant requestedPublishedAt) {
        if (status == CampaignStatus.PUBLISHED && requestedPublishedAt == null) {
            return Instant.now(clock);
        }
        return requestedPublishedAt;
    }

    /**
     * Tells whether the requested texts differ from the stored ones: a language added or
     * left out, or a text with other content. Read before the stored texts are overwritten.
     */
    private static boolean textsChanged(List<CampaignContent> stored, List<CampaignContentRequest> requested) {
        Map<DocumentLanguage, String> storedByLanguage = new EnumMap<>(DocumentLanguage.class);
        for (CampaignContent content : stored) {
            storedByLanguage.put(content.getLanguage(), content.getText());
        }
        Map<DocumentLanguage, String> requestedByLanguage = new EnumMap<>(DocumentLanguage.class);
        if (requested != null) {
            for (CampaignContentRequest content : requested) {
                requestedByLanguage.put(content.language(), content.text());
            }
        }
        return !storedByLanguage.equals(requestedByLanguage);
    }

    /**
     * Makes the stored texts of a campaign equal to the requested ones: a text whose
     * language is left out is deleted, a text in a stored language is overwritten in place,
     * and a new language is inserted. No language is deleted and inserted again, so the
     * unique index on campaign and language never sees two rows at once.
     *
     * @return the texts after the change, in the order NL, TR, EN
     */
    private List<CampaignContent> replaceContents(UUID campaignId, List<CampaignContent> stored,
                                                  List<CampaignContentRequest> requested) {
        Map<DocumentLanguage, CampaignContentRequest> requestedByLanguage = new EnumMap<>(DocumentLanguage.class);
        if (requested != null) {
            for (CampaignContentRequest content : requested) {
                requestedByLanguage.put(content.language(), content);
            }
        }

        List<CampaignContent> removed = new ArrayList<>();
        List<CampaignContent> kept = new ArrayList<>();
        for (CampaignContent content : stored) {
            CampaignContentRequest replacement = requestedByLanguage.remove(content.getLanguage());
            if (replacement == null) {
                removed.add(content);
            } else {
                content.setText(replacement.text());
                kept.add(content);
            }
        }
        for (CampaignContentRequest content : requestedByLanguage.values()) {
            CampaignContent added = campaignMapper.toContentEntity(content);
            added.setCampaignId(campaignId);
            kept.add(added);
        }

        campaignContentRepository.deleteAll(removed);
        // Flushed here so the response carries the creation and last-change time of each text.
        List<CampaignContent> saved = new ArrayList<>(campaignContentRepository.saveAllAndFlush(kept));
        saved.sort(Comparator.comparing(CampaignContent::getLanguage));
        return saved;
    }

    /**
     * Maps a campaign and its texts to the detail response, with the owner's name.
     */
    private CampaignDetailResponse toDetailResponse(Campaign campaign, List<CampaignContent> contents) {
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
