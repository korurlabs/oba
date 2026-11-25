package com.korur.oba.apps.otak.campaign.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * One content of a campaign with the short name of the legal entity that publishes it.
 */
@Schema(description = "Campaign content")
public record CampaignContentResponse(

        @Schema(description = "Content id", example = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d")
        UUID id,

        @Schema(description = "Campaign the content belongs to", example = "6c7d8e9f-0a1b-4c2d-8e3f-4a5b6c7d8e9f")
        UUID campaignId,

        @Schema(description = "Legal entity that publishes the content", example = "3e4f5a6b-7c8d-4e9f-8a0b-1c2d3e4f5a6b")
        UUID legalEntityId,

        @Schema(description = "Short name of the legal entity", example = "Korur B.V.")
        String legalEntityShortName,

        @Schema(description = "Channel the content goes out on", example = "LINKEDIN")
        CampaignChannel channel,

        @Schema(description = "Language of the text", example = "NL")
        DocumentLanguage language,

        @Schema(description = "Plain text of the post", example = "Terwijl u slaapt, houden wij de wacht.")
        String text,

        @Schema(description = "Status of the content", example = "SCHEDULED")
        CampaignStatus status,

        @Schema(description = "Moment the content is planned to go live", nullable = true,
                example = "2026-12-01T08:30:00Z")
        Instant plannedAt,

        @Schema(description = "Moment the content went live", nullable = true, example = "2026-12-01T08:32:00Z")
        Instant publishedAt,

        @Schema(description = "Address of the live post", nullable = true,
                example = "https://www.linkedin.com/posts/korur_de-wacht")
        String postUrl,

        @Schema(description = "Creation timestamp", example = "2026-11-25T09:00:00Z")
        Instant createdAt,

        @Schema(description = "Last change of the content", example = "2026-11-25T09:30:00Z")
        Instant updatedAt
) {
}
