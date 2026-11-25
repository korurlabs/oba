package com.korur.oba.apps.otak.campaign.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 2. Third-party
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

// 3. Java SDK
import java.util.Set;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;

class CampaignUpdateRequestValidationUnitTest {

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

    private static Set<String> violated(CampaignUpdateRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.toSet());
    }

    @Test
    void requiredFieldsWithStatus_isValid() {
        assertThat(violated(new CampaignUpdateRequest("IGEF", CampaignChannel.NEWSLETTER, CampaignStatus.DRAFT,
                null, null, null, null, null, null, null))).isEmpty();
    }

    @Test
    void missingStatus_isRejected() {
        assertThat(violated(new CampaignUpdateRequest("IGEF", CampaignChannel.NEWSLETTER, null,
                null, null, null, null, null, null, null)))
                .containsExactly("status: " + CampaignConstant.STATUS_REQUIRED_MESSAGE);
    }
}
