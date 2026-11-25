package com.korur.oba.apps.otak.campaign.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * The text of a campaign in one language.
 */
@Schema(description = "Campaign text in one language")
public record CampaignContentResponse(

        @Schema(description = "Language of the text", example = "NL")
        DocumentLanguage language,

        @Schema(description = "Plain text of the post in this language", example = "Meet us at IGEF 2026 in hall 4.")
        String text,

        @Schema(description = "Last change of this text", example = "2026-11-25T09:30:00Z")
        Instant updatedAt
) {
}
