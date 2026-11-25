package com.korur.oba.apps.otak.campaign.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * The text of a campaign in one language, an entry of the contents of
 * {@link CampaignCreateRequest} and {@link CampaignUpdateRequest}. The text is plain text,
 * never blank; it is stored as sent, so line
 * breaks and spacing of the post survive.
 *
 * @param language the language of the text; required, at most once per campaign
 * @param text     the text; required, not blank
 */
@Schema(description = "Campaign text in one language")
public record CampaignContentRequest(

        @Schema(description = "Language of the text", requiredMode = Schema.RequiredMode.REQUIRED, example = "NL")
        @NotNull(message = CampaignConstant.CONTENT_LANGUAGE_REQUIRED_MESSAGE)
        DocumentLanguage language,

        @Schema(description = "Plain text of the post in this language",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "Meet us at IGEF 2026 in hall 4.")
        @NotBlank(message = CampaignConstant.CONTENT_TEXT_REQUIRED_MESSAGE)
        String text
) {
}
