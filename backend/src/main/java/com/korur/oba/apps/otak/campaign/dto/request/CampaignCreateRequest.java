package com.korur.oba.apps.otak.campaign.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.apps.otak.relation.constant.RelationConstant;
import com.korur.oba.dojo.annotation.NullOrNotBlank;

/**
 * Request payload used to create a campaign with its texts, the body of
 * {@link com.korur.oba.apps.otak.campaign.controller.CampaignController#createCampaign}. The
 * organization comes from the checked request header, never from the body, so a campaign can
 * not be written into another organization.
 *
 * Field rules, checked by Bean Validation before the service runs (422 GEN_VAL_001): the
 * name and the channel are required; the post URL is null or a full http or https address,
 * never blank; at most three texts, each with a language and a text that is not blank. The
 * moments are instants, sent with Z or an offset and stored in UTC.
 *
 * Rules that need several fields or the stored data are checked in
 * {@link com.korur.oba.apps.otak.campaign.service.impl.CampaignServiceImpl#create}:
 *
 * - {@link com.korur.oba.apps.otak.campaign.validator.CampaignValidator#ensureStatusDates}
 *   refuses a SCHEDULED campaign without plannedAt, and a DRAFT or SCHEDULED one with
 *   publishedAt,
 *   {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CAMPAIGN_STATUS_DATES}
 *   (OTK_VAL_016, 422); a PUBLISHED campaign without publishedAt gets the server's now;
 * - {@link com.korur.oba.apps.otak.campaign.validator.CampaignValidator#ensureLanguagesDistinct}
 *   refuses two texts in one language,
 *   {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CAMPAIGN_CONTENT_LANGUAGE_REPEATED}
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
 * @param channel     the channel the campaign goes out on; required
 * @param status      the status, or null for DRAFT
 * @param plannedAt   the moment the campaign is planned to go live; required while SCHEDULED
 * @param publishedAt the moment the campaign went live; none while DRAFT or SCHEDULED
 * @param postUrl     the address of the live post, or null
 * @param ownerUserId the member who looks after the campaign, or null
 * @param eventId     the event of the organization the campaign belongs to, or null
 * @param icpId       the ICP of the organization the campaign is aimed at, or null
 * @param contents    the texts, at most one per language; null for none
 * @see CampaignUpdateRequest
 */
@Schema(description = "Campaign create request")
public record CampaignCreateRequest(

        @Schema(description = "Name of the campaign, unique within the organization with case ignored",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "IGEF 2026 announcement")
        @NotBlank(message = CampaignConstant.NAME_REQUIRED_MESSAGE)
        @Size(max = CampaignConstant.NAME_MAX_LENGTH, message = CampaignConstant.NAME_SIZE_MESSAGE)
        String name,

        @Schema(description = "Channel the campaign goes out on", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "LINKEDIN")
        @NotNull(message = CampaignConstant.CHANNEL_REQUIRED_MESSAGE)
        CampaignChannel channel,

        @Schema(description = "Status of the campaign; null for DRAFT", nullable = true, example = "DRAFT")
        CampaignStatus status,

        @Schema(description = "Moment the campaign is planned to go live (ISO 8601 with Z or an offset); "
                + "required while SCHEDULED", nullable = true, example = "2026-12-01T08:30:00Z")
        Instant plannedAt,

        @Schema(description = "Moment the campaign went live (ISO 8601 with Z or an offset); none while DRAFT or "
                + "SCHEDULED, the server's now when PUBLISHED without one", nullable = true,
                example = "2026-12-01T08:32:00Z")
        Instant publishedAt,

        @Schema(description = "Address of the live post, a full http or https address; null when there is none",
                nullable = true, example = "https://www.linkedin.com/posts/korur_igef-2026")
        @NullOrNotBlank(message = CampaignConstant.POST_URL_BLANK_MESSAGE)
        @Size(max = CampaignConstant.POST_URL_MAX_LENGTH, message = CampaignConstant.POST_URL_SIZE_MESSAGE)
        @Pattern(regexp = RelationConstant.WEBSITE_PATTERN, message = CampaignConstant.POST_URL_PATTERN_MESSAGE)
        String postUrl,

        @Schema(description = "Member who looks after the campaign; null for none", nullable = true,
                example = "4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a")
        UUID ownerUserId,

        @Schema(description = "Event of the organization the campaign belongs to; null for none", nullable = true,
                example = "8a1d3c5e-2b4f-4e6a-9c7d-1f2e3a4b5c6d")
        UUID eventId,

        @Schema(description = "ICP of the organization the campaign is aimed at; null for none", nullable = true,
                example = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e")
        UUID icpId,

        @Schema(description = "Texts of the campaign, at most one per language (NL, TR, EN); null for none",
                nullable = true)
        @Size(max = CampaignConstant.CONTENTS_MAX_SIZE, message = CampaignConstant.CONTENTS_SIZE_MESSAGE)
        List<@NotNull(message = CampaignConstant.CONTENT_REQUIRED_MESSAGE) @Valid CampaignContentRequest> contents
) {
}
