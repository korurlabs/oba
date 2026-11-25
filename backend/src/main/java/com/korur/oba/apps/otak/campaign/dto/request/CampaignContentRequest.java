package com.korur.oba.apps.otak.campaign.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.apps.otak.relation.constant.RelationConstant;
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * Request payload used to create or replace one content of a campaign, the body of
 * {@link com.korur.oba.apps.otak.campaign.controller.CampaignContentController#createContent} and
 * {@link com.korur.oba.apps.otak.campaign.controller.CampaignContentController#updateContent}. The
 * organization comes from the checked request header and the campaign from the path, never
 * from the body.
 *
 * Field rules, checked by Bean Validation before the service runs (422 GEN_VAL_001): the
 * legal entity, channel and language are required and the text is not blank; the post URL is
 * null or a full http or https address, never blank; no field has a length limit. The
 * moments are instants, sent with Z or an offset and stored in UTC. The text is stored as
 * sent, so line breaks and spacing of the post survive.
 *
 * Rules that need several fields or the stored data are checked in
 * {@link com.korur.oba.apps.otak.campaign.service.impl.CampaignContentServiceImpl}:
 *
 * - the status is optional on create (DRAFT) and required on update (GEN_VAL_001, 422);
 * - {@link com.korur.oba.apps.otak.campaign.validator.CampaignContentValidator#ensureStatusDates}
 *   refuses a SCHEDULED content without plannedAt, a DRAFT or SCHEDULED one with
 *   publishedAt, and a publishedAt in the future,
 *   {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CAMPAIGN_CONTENT_STATUS_DATES}
 *   (OTK_VAL_016, 422); a PUBLISHED content without publishedAt gets the server's now;
 * - the legal entity must belong to the organization (LGE_NOT_001, 404). No rule ties the
 *   language to the legal entity.
 *
 * Sent by the Otak web app and by the oba CLI (oba otak campaign content).
 *
 * @param legalEntityId the legal entity of the organization that publishes the content; required
 * @param channel       the channel the content goes out on; required
 * @param language      the language of the text; required
 * @param text          the text; required, not blank
 * @param status        the status; null for DRAFT on create, required on update
 * @param plannedAt     the moment the content is planned to go live; required while SCHEDULED
 * @param publishedAt   the moment the content went live; none while DRAFT or SCHEDULED
 * @param postUrl       the address of the live post, or null
 */
@Schema(description = "Campaign content create or update request")
public record CampaignContentRequest(

        @Schema(description = "Legal entity of the organization that publishes the content",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "3e4f5a6b-7c8d-4e9f-8a0b-1c2d3e4f5a6b")
        @NotNull(message = CampaignConstant.LEGAL_ENTITY_REQUIRED_MESSAGE)
        UUID legalEntityId,

        @Schema(description = "Channel the content goes out on", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "LINKEDIN")
        @NotNull(message = CampaignConstant.CHANNEL_REQUIRED_MESSAGE)
        CampaignChannel channel,

        @Schema(description = "Language of the text", requiredMode = Schema.RequiredMode.REQUIRED, example = "NL")
        @NotNull(message = CampaignConstant.LANGUAGE_REQUIRED_MESSAGE)
        DocumentLanguage language,

        @Schema(description = "Plain text of the post", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Terwijl u slaapt, houden wij de wacht.")
        @NotBlank(message = CampaignConstant.TEXT_REQUIRED_MESSAGE)
        String text,

        @Schema(description = "Status of the content; null for DRAFT on create, required on update",
                nullable = true, example = "DRAFT")
        CampaignStatus status,

        @Schema(description = "Moment the content is planned to go live (ISO 8601 with Z or an offset); "
                + "required while SCHEDULED", nullable = true, example = "2026-12-01T08:30:00Z")
        Instant plannedAt,

        @Schema(description = "Moment the content went live (ISO 8601 with Z or an offset), not in the future; "
                + "none while DRAFT or SCHEDULED, the server's now when PUBLISHED without one", nullable = true,
                example = "2026-12-01T08:32:00Z")
        Instant publishedAt,

        @Schema(description = "Address of the live post, a full http or https address; null when there is none",
                nullable = true, example = "https://www.linkedin.com/posts/korur_de-wacht")
        @NullOrNotBlank(message = CampaignConstant.POST_URL_BLANK_MESSAGE)
        @Pattern(regexp = RelationConstant.WEBSITE_PATTERN, message = CampaignConstant.POST_URL_PATTERN_MESSAGE)
        String postUrl
) {
}
