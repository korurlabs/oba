package com.korur.oba.apps.otak.campaign.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 2. Third-party
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

// 3. Java SDK
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

class CampaignContentRequestValidationUnitTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    private static Set<String> violated(Object request) {
        Set<String> violations = new HashSet<>();
        for (ConstraintViolation<Object> violation : validator.validate(request)) {
            violations.add(violation.getPropertyPath() + ": " + violation.getMessage());
        }
        return violations;
    }

    private static CampaignContentRequest request(String text, String postUrl) {
        return new CampaignContentRequest(UUID.randomUUID(), CampaignChannel.LINKEDIN, DocumentLanguage.NL, text,
                null, null, null, postUrl);
    }

    @Test
    void requiredFieldsWithoutStatus_isValid() {
        assertThat(violated(request("Terwijl u slaapt, houden wij de wacht.", null))).isEmpty();
    }

    @Test
    void missingRequiredFieldsAndBlankText_areRejected() {
        CampaignContentRequest request = new CampaignContentRequest(null, null, null, " \n ", null, null, null, null);

        assertThat(violated(request)).containsExactlyInAnyOrder(
                "legalEntityId: " + CampaignConstant.LEGAL_ENTITY_REQUIRED_MESSAGE,
                "channel: " + CampaignConstant.CHANNEL_REQUIRED_MESSAGE,
                "language: " + CampaignConstant.LANGUAGE_REQUIRED_MESSAGE,
                "text: " + CampaignConstant.TEXT_REQUIRED_MESSAGE);
    }

    @Test
    void longTextAndPostUrl_areAcceptedWithoutSizeLimit() {
        assertThat(violated(request("t".repeat(50000), "https://www.linkedin.com/" + "p".repeat(5000)))).isEmpty();
    }

    @Test
    void postUrl_mustBeNullOrAFullAddress() {
        assertThat(violated(request("Hallo", " "))).contains("postUrl: " + CampaignConstant.POST_URL_BLANK_MESSAGE);
        assertThat(violated(request("Hallo", "linkedin.com/posts/1")))
                .containsExactly("postUrl: " + CampaignConstant.POST_URL_PATTERN_MESSAGE);
        assertThat(violated(request("Hallo", "https://www.linkedin.com/posts/korur_de-wacht"))).isEmpty();
    }
}
