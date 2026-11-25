package com.korur.oba.apps.otak.campaign.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * One row of the campaign list. It names the languages the campaign has a text in, not the
 * texts, so the whole list stays small.
 */
@Schema(description = "Campaign list item")
public record CampaignListResponse(

        @Schema(description = "Campaign id", example = "6c7d8e9f-0a1b-4c2d-8e3f-4a5b6c7d8e9f")
        UUID id,

        @Schema(description = "Name of the campaign", example = "IGEF 2026 announcement")
        String name,

        @Schema(description = "Channel the campaign goes out on", example = "LINKEDIN")
        CampaignChannel channel,

        @Schema(description = "Status of the campaign", example = "SCHEDULED")
        CampaignStatus status,

        @Schema(description = "Moment the campaign is planned to go live", nullable = true,
                example = "2026-12-01T08:30:00Z")
        Instant plannedAt,

        @Schema(description = "Moment the campaign went live", nullable = true, example = "2026-12-01T08:32:00Z")
        Instant publishedAt,

        @Schema(description = "Languages the campaign has a text in, in the order NL, TR, EN", example = "[\"NL\", \"EN\"]")
        List<DocumentLanguage> languages,

        @Schema(description = "Member who looks after the campaign; null for none", nullable = true)
        CampaignOwnerResponse owner,

        @Schema(description = "Event the campaign belongs to", nullable = true,
                example = "8a1d3c5e-2b4f-4e6a-9c7d-1f2e3a4b5c6d")
        UUID eventId,

        @Schema(description = "ICP the campaign is aimed at", nullable = true,
                example = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e")
        UUID icpId,

        @Schema(description = "Creation timestamp", example = "2026-11-25T09:00:00Z")
        Instant createdAt,

        @Schema(description = "Last change of the campaign fields", example = "2026-11-25T09:30:00Z")
        Instant updatedAt
) {
}
