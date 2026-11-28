package com.korur.oba.apps.otak.campaign.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.apps.otak.campaign.constant.CampaignExceptionLogConstant;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
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
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Validator for campaign lookups, the name rule, the date range and the campaign owner.
 */
@Component
@RequiredArgsConstructor
public class CampaignValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CampaignValidator.class);

    private final CampaignRepository campaignRepository;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AppAccessValidator appAccessValidator;
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
     * Ensures the end date is not before the start date, the same rule the check constraint
     * campaigns_dates_ck enforces. A campaign without one of the dates always passes.
     *
     * @param startDate the first day of the campaign, or null
     * @param endDate   the last day of the campaign, or null
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_CAMPAIGN_DATE_RANGE} when
     *         the end date is before the start date
     */
    public void ensureDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return;
        }
        if (endDate.isBefore(startDate)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    CampaignExceptionLogConstant.CAMPAIGN_DATE_RANGE_INVALID, startDate, endDate);
            throw new ValidationException(
                    ExceptionDomainOtakError.VAL_CAMPAIGN_DATE_RANGE,
                    Map.of(CampaignConstant.FIELD_START_DATE, startDate,
                            CampaignConstant.FIELD_END_DATE, endDate));
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
        Optional<OrganizationMember> member = organizationMemberQueryService.findMember(organizationId, ownerUserId);
        boolean activeMember = member.isPresent() && member.get().getStatus() == OrganizationMemberStatus.ACTIVE;
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
