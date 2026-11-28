package com.korur.oba.apps.otak.campaign.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.apps.otak.campaign.constant.CampaignExceptionLogConstant;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.apps.otak.campaign.repository.CampaignContentRepository;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for campaign content lookups and the moments each status allows.
 */
@Component
@RequiredArgsConstructor
public class CampaignContentValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CampaignContentValidator.class);

    private final CampaignContentRepository campaignContentRepository;
    private final Clock clock;
    private final LoggingService loggingService;

    /**
     * Ensures the campaign of the organization has the content and returns it. A content of
     * another campaign or organization answers the same not found as a missing one, so its id
     * reveals nothing to another tenant.
     *
     * @param organizationId the organization the caller acts in
     * @param campaignId     the campaign from the path
     * @param contentId      the content identifier
     * @return the content
     * @throws NotFoundException {@link ExceptionDomainOtakError#NOT_CAMPAIGN_CONTENT_FOUND} if
     *         the campaign of the organization has no content with the id
     */
    public CampaignContent ensureContentExists(UUID organizationId, UUID campaignId, UUID contentId) {
        return campaignContentRepository.findByIdAndCampaignIdAndOrganizationId(contentId, campaignId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            CampaignExceptionLogConstant.CAMPAIGN_CONTENT_NOT_FOUND, contentId, campaignId,
                            organizationId);
                    return new NotFoundException(
                            ExceptionDomainOtakError.NOT_CAMPAIGN_CONTENT_FOUND,
                            Map.of(CampaignConstant.FIELD_CONTENT_ID, contentId));
                });
    }

    /**
     * Ensures a content with the id exists in any organization and returns it, for the access
     * check of an attachment whose parent is a content. The caller compares the content's
     * organization with the request's.
     *
     * @param contentId the content identifier
     * @return the content
     * @throws NotFoundException {@link ExceptionDomainOtakError#NOT_CAMPAIGN_CONTENT_FOUND} if
     *         no content has the id
     */
    public CampaignContent ensureContentExists(UUID contentId) {
        return campaignContentRepository.findById(contentId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            CampaignExceptionLogConstant.CAMPAIGN_CONTENT_NOT_FOUND_BY_ID, contentId);
                    return new NotFoundException(
                            ExceptionDomainOtakError.NOT_CAMPAIGN_CONTENT_FOUND,
                            Map.of(CampaignConstant.FIELD_CONTENT_ID, contentId));
                });
    }

    /**
     * Ensures an update names the status. One request serves create and update, and only
     * create may leave the status out (DRAFT); an update replaces every field, so a missing
     * status would have nothing to replace it with.
     *
     * The refusal is a field error on the status, so a form shows it next to the field.
     *
     * @param contentId the content being updated
     * @param status    the status of the request
     * @throws ValidationException {@link ExceptionValidationError#VALIDATION} (GEN_VAL_001) on
     *         {@link CampaignConstant#FIELD_STATUS} when the status is null
     */
    public void ensureStatusGiven(UUID contentId, CampaignStatus status) {
        if (status != null) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                CampaignExceptionLogConstant.CAMPAIGN_CONTENT_STATUS_MISSING, contentId);
        throw new ValidationException(ExceptionValidationError.VALIDATION,
                Map.of(CampaignConstant.FIELD_STATUS, CampaignConstant.STATUS_REQUIRED_MESSAGE));
    }

    /**
     * Ensures the moments fit the status, the same rules the two check constraints of
     * otak.campaign_contents enforce: a SCHEDULED content has a planned moment, and a DRAFT or
     * SCHEDULED content was not published, so it has no published moment. A published moment
     * is never after the {@link Clock}'s now: a post that goes live later is SCHEDULED with a
     * planned moment, not published ahead. A PUBLISHED content without a published moment gets
     * the server's now afterwards.
     *
     * @param status      the status to store
     * @param plannedAt   the planned moment, or null
     * @param publishedAt the published moment, or null
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_CAMPAIGN_CONTENT_STATUS_DATES}
     *         when a moment does not fit the status or the published moment is in the future
     */
    public void ensureStatusDates(CampaignStatus status, Instant plannedAt, Instant publishedAt) {
        boolean scheduledWithoutPlan = status == CampaignStatus.SCHEDULED && plannedAt == null;
        boolean unpublishedWithDate = (status == CampaignStatus.DRAFT || status == CampaignStatus.SCHEDULED)
                && publishedAt != null;
        boolean publishedInFuture = publishedAt != null && publishedAt.isAfter(Instant.now(clock));
        if (scheduledWithoutPlan || unpublishedWithDate || publishedInFuture) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    CampaignExceptionLogConstant.CAMPAIGN_CONTENT_STATUS_DATES_INVALID, status, plannedAt,
                    publishedAt);
            // HashMap, since Map.of refuses the null moments the client is told about.
            Map<String, Object> details = new HashMap<>();
            details.put(CampaignConstant.FIELD_STATUS, status);
            details.put(CampaignConstant.FIELD_PLANNED_AT, plannedAt);
            details.put(CampaignConstant.FIELD_PUBLISHED_AT, publishedAt);
            throw new ValidationException(ExceptionDomainOtakError.VAL_CAMPAIGN_CONTENT_STATUS_DATES, details);
        }
    }
}
