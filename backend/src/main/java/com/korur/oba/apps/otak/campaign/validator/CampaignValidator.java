package com.korur.oba.apps.otak.campaign.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.apps.otak.campaign.constant.CampaignExceptionLogConstant;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignContentRequest;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.apps.otak.campaign.repository.CampaignRepository;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Validator for campaign lookups, the name rule, the moments of each status, the languages
 * of the texts and the campaign owner.
 */
@Component
@RequiredArgsConstructor
public class CampaignValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CampaignValidator.class);

    private final CampaignRepository campaignRepository;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AppAccessValidator appAccessValidator;
    private final Clock clock;
    private final LoggingService loggingService;

    /**
     * Ensures the organization has the campaign and returns it. A campaign of another
     * organization answers the same not found as a missing one, so its id reveals nothing to
     * another tenant.
     *
     * @param organizationId the organization the caller acts in
     * @param campaignId     the campaign identifier
     * @return the campaign
     * @throws NotFoundException {@link ExceptionDomainOtakError#NOT_CAMPAIGN_FOUND} if the
     *         organization has no campaign with the id
     */
    public Campaign ensureCampaignExists(UUID organizationId, UUID campaignId) {
        return campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            CampaignExceptionLogConstant.CAMPAIGN_NOT_FOUND, campaignId, organizationId);
                    return new NotFoundException(
                            ExceptionDomainOtakError.NOT_CAMPAIGN_FOUND,
                            Map.of(CampaignConstant.FIELD_CAMPAIGN_ID, campaignId));
                });
    }

    /**
     * Ensures a campaign with the id exists in any organization and returns it, for the
     * access check of an attachment whose parent is a campaign. The caller compares the
     * campaign's organization with the request's.
     *
     * @param campaignId the campaign identifier
     * @return the campaign
     * @throws NotFoundException {@link ExceptionDomainOtakError#NOT_CAMPAIGN_FOUND} if no
     *         campaign has the id
     */
    public Campaign ensureCampaignExists(UUID campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            CampaignExceptionLogConstant.CAMPAIGN_NOT_FOUND_BY_ID, campaignId);
                    return new NotFoundException(
                            ExceptionDomainOtakError.NOT_CAMPAIGN_FOUND,
                            Map.of(CampaignConstant.FIELD_CAMPAIGN_ID, campaignId));
                });
    }

    /**
     * Ensures no other campaign of the organization has the name, case ignored, so a campaign
     * can be picked by its name without ambiguity, as the CLI and a seed do.
     *
     * @param organizationId   the organization that owns the campaigns
     * @param name             the trimmed name to check
     * @param exceptCampaignId the campaign that may keep its own name on an update, or null on create
     * @throws ConflictException {@link ExceptionDomainOtakError#STA_CAMPAIGN_NAME_TAKEN} when
     *         another campaign of the organization has the name
     */
    public void ensureNameFree(UUID organizationId, String name, UUID exceptCampaignId) {
        boolean taken = exceptCampaignId == null
                ? campaignRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, name)
                : campaignRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, name,
                        exceptCampaignId);
        if (taken) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    CampaignExceptionLogConstant.CAMPAIGN_NAME_TAKEN, organizationId, name);
            throw new ConflictException(
                    ExceptionDomainOtakError.STA_CAMPAIGN_NAME_TAKEN,
                    Map.of(CampaignConstant.FIELD_NAME, name));
        }
    }

    /**
     * Ensures the moments fit the status, the same rules the two check constraints of
     * otak.campaigns enforce: a SCHEDULED campaign has a planned moment, and a DRAFT or
     * SCHEDULED campaign was not published, so it has no published moment. PUBLISHED and
     * ARCHIVED take any planned moment and a published moment up to the {@link Clock}'s now: a
     * post that goes live later is SCHEDULED with a planned moment, not published ahead. A
     * PUBLISHED campaign without a published moment gets the server's now afterwards.
     *
     * @param status      the status to store
     * @param plannedAt   the planned moment, or null
     * @param publishedAt the published moment, or null
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_CAMPAIGN_STATUS_DATES}
     *         when a moment does not fit the status or the published moment is in the future
     */
    public void ensureStatusDates(CampaignStatus status, Instant plannedAt, Instant publishedAt) {
        boolean scheduledWithoutPlan = status == CampaignStatus.SCHEDULED && plannedAt == null;
        boolean unpublishedWithDate = (status == CampaignStatus.DRAFT || status == CampaignStatus.SCHEDULED)
                && publishedAt != null;
        boolean publishedInFuture = publishedAt != null && publishedAt.isAfter(Instant.now(clock));
        if (scheduledWithoutPlan || unpublishedWithDate || publishedInFuture) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    CampaignExceptionLogConstant.CAMPAIGN_STATUS_DATES_INVALID, status, plannedAt, publishedAt);
            // HashMap, since Map.of refuses the null moments the client is told about.
            Map<String, Object> details = new HashMap<>();
            details.put(CampaignConstant.FIELD_STATUS, status);
            details.put(CampaignConstant.FIELD_PLANNED_AT, plannedAt);
            details.put(CampaignConstant.FIELD_PUBLISHED_AT, publishedAt);
            throw new ValidationException(ExceptionDomainOtakError.VAL_CAMPAIGN_STATUS_DATES, details);
        }
    }

    /**
     * Ensures the texts name each language at most once, the rule the unique index of
     * otak.campaign_contents enforces, so a repeated language is a clear 422 instead of a
     * database error. No texts always pass.
     *
     * @param contents the texts of the request, or null for none
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_CAMPAIGN_CONTENT_LANGUAGE_REPEATED}
     *         when two texts have one language
     */
    public void ensureLanguagesDistinct(List<CampaignContentRequest> contents) {
        if (contents == null) {
            return;
        }
        Set<DocumentLanguage> seen = EnumSet.noneOf(DocumentLanguage.class);
        for (CampaignContentRequest content : contents) {
            if (!seen.add(content.language())) {
                loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                        CampaignExceptionLogConstant.CAMPAIGN_CONTENT_LANGUAGE_REPEATED, content.language());
                throw new ValidationException(
                        ExceptionDomainOtakError.VAL_CAMPAIGN_CONTENT_LANGUAGE_REPEATED,
                        Map.of(CampaignConstant.FIELD_LANGUAGE, content.language()));
            }
        }
    }

    /**
     * Ensures a campaign owner is an ACTIVE member of the organization who may open Otak
     * there, so a campaign is never handed to someone outside the tenant or to a member who
     * could not see it. An unknown user gets the same answer as a non-member, so the check
     * reveals nothing about other organizations. A null owner means no owner and passes.
     *
     * @param organizationId the organization that owns the campaign
     * @param ownerUserId    the owner to set, or null for none
     * @throws ConflictException {@link ExceptionDomainOtakError#STA_OWNER_NOT_ORGANIZATION_MEMBER}
     *         when the owner is no active member of the organization
     */
    public void ensureOwnerIsOrganizationMember(UUID organizationId, UUID ownerUserId) {
        if (ownerUserId == null) {
            return;
        }
        boolean activeMember = organizationMemberQueryService.findMember(organizationId, ownerUserId)
                .map((OrganizationMember member) -> member.getStatus() == OrganizationMemberStatus.ACTIVE)
                .orElse(false);
        if (!activeMember) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    CampaignExceptionLogConstant.OWNER_NOT_ORGANIZATION_MEMBER, organizationId, ownerUserId);
            throw new ConflictException(
                    ExceptionDomainOtakError.STA_OWNER_NOT_ORGANIZATION_MEMBER,
                    Map.of(CampaignConstant.FIELD_ORGANIZATION_ID, organizationId,
                            CampaignConstant.FIELD_OWNER_USER_ID, ownerUserId));
        }
        appAccessValidator.ensureUsersHaveAppAccess(organizationId, App.OTAK, List.of(ownerUserId));
    }
}
