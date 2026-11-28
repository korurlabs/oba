package com.korur.oba.apps.otak.campaign.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;

/**
 * Request payload used to create a campaign, the body of
 * {@link com.korur.oba.apps.otak.campaign.controller.CampaignController#createCampaign}. The
 * organization comes from the checked request header, never from the body, so a campaign can
 * not be written into another organization. Its contents are added afterwards through
 * {@link com.korur.oba.apps.otak.campaign.controller.CampaignContentController}.
 *
 * Field rules, checked by Bean Validation before the service runs (422 GEN_VAL_001): the
 * name is required and not blank; no field has a length limit. The dates are calendar days
 * (YYYY-MM-DD) without a timezone.
 *
 * Rules that need several fields or the stored data are checked in
 * {@link com.korur.oba.apps.otak.campaign.service.impl.CampaignServiceImpl#create}:
 *
 * - {@link com.korur.oba.apps.otak.campaign.validator.CampaignValidator#ensureDateRange}
 *   refuses an end date before the start date,
 *   {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CAMPAIGN_DATE_RANGE}
 *   (OTK_VAL_017, 422);
 * - {@link com.korur.oba.apps.otak.campaign.validator.CampaignValidator#ensureNameFree} refuses
 *   a name another campaign of the organization already has, case ignored,
 *   {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_CAMPAIGN_NAME_TAKEN}
 *   (OTK_STA_015, 409), so a campaign can be picked by its name without ambiguity;
 * - the owner must be an active member of the organization who may open Otak (OTK_STA_001,
 *   409), and the event and the ICP must belong to the organization (OTK_NOT_009, OTK_NOT_004, 404).
 *
 * The request is mapped with {@link com.korur.oba.apps.otak.campaign.mapper.CampaignMapper#toEntity}.
 * Sent by the Otak web app and by the oba CLI (oba otak campaign).
 *
 * @param name        the campaign name, unique within the organization with case ignored;
 *                    required, stored trimmed
 * @param description what the campaign is about, or null
 * @param startDate   the first day of the campaign, or null
 * @param endDate     the last day of the campaign, or null; not before the start date
 * @param ownerUserId the member who looks after the campaign, or null
 * @param eventId     the event of the organization the campaign belongs to, or null
 * @param icpId       the ICP of the organization the campaign is aimed at, or null
 * @see CampaignUpdateRequest
 */
@Schema(description = "Campaign create request")
public record CampaignCreateRequest(

        @Schema(description = "Name of the campaign, unique within the organization with case ignored",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "De wacht")
        @NotBlank(message = CampaignConstant.NAME_REQUIRED_MESSAGE)
        String name,

        @Schema(description = "What the campaign is about, plain text; null for none", nullable = true,
                example = "Post about the night watch of a security operations centre")
        String description,

        @Schema(description = "First day of the campaign (YYYY-MM-DD); null for none", nullable = true,
                example = "2026-12-01")
        LocalDate startDate,

        @Schema(description = "Last day of the campaign (YYYY-MM-DD), not before the start date; null for none",
                nullable = true, example = "2026-12-31")
        LocalDate endDate,

        @Schema(description = "Member who looks after the campaign; null for none", nullable = true,
                example = "4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a")
        UUID ownerUserId,

        @Schema(description = "Event of the organization the campaign belongs to; null for none", nullable = true,
                example = "8a1d3c5e-2b4f-4e6a-9c7d-1f2e3a4b5c6d")
        UUID eventId,

        @Schema(description = "ICP of the organization the campaign is aimed at; null for none", nullable = true,
                example = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e")
        UUID icpId
) {
}
