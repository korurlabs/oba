package com.korur.oba.apps.otak.campaign.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal

/**
 * Every field of one campaign. Its contents are read through the nested contents list.
 */
@Schema(description = "Campaign detail")
public record CampaignDetailResponse(

        @Schema(description = "Campaign id", example = "6c7d8e9f-0a1b-4c2d-8e3f-4a5b6c7d8e9f")
        UUID id,

        @Schema(description = "Name of the campaign", example = "De wacht")
        String name,

        @Schema(description = "What the campaign is about", nullable = true,
                example = "Post about the night watch of a security operations centre")
        String description,

        @Schema(description = "First day of the campaign", nullable = true, example = "2026-12-01")
        LocalDate startDate,

        @Schema(description = "Last day of the campaign", nullable = true, example = "2026-12-31")
        LocalDate endDate,

        @Schema(description = "Member who looks after the campaign; null for none", nullable = true)
        CampaignOwnerResponse owner,

        @Schema(description = "Event the campaign belongs to", nullable = true,
                example = "8a1d3c5e-2b4f-4e6a-9c7d-1f2e3a4b5c6d")
        UUID eventId,

        @Schema(description = "ICP the campaign is aimed at", nullable = true,
                example = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e")
        UUID icpId,

        @Schema(description = "Number of contents of the campaign", example = "3")
        long contentCount,

        @Schema(description = "Creation timestamp", example = "2026-11-25T09:00:00Z")
        Instant createdAt,

        @Schema(description = "Last change of the campaign fields", example = "2026-11-25T09:30:00Z")
        Instant updatedAt
) {
}
