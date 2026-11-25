package com.korur.oba.apps.otak.campaign.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * The campaign and language of one text, a row of
 * {@link com.korur.oba.apps.otak.campaign.repository.CampaignContentRepository#findLanguages}, read
 * for the whole campaign list without loading the texts.
 */
public interface CampaignContentLanguageProjection {

    /**
     * @return the campaign id
     */
    UUID getCampaignId();

    /**
     * @return the language of the text
     */
    DocumentLanguage getLanguage();
}
