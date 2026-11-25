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
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;

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

    private static Set<String> violated(Object request) {
        Set<String> violations = new HashSet<>();
        for (ConstraintViolation<Object> violation : validator.validate(request)) {
            violations.add(violation.getPropertyPath() + ": " + violation.getMessage());
        }
        return violations;
    }

    @Test
    void everyField_isValid() {
        assertThat(violated(new CampaignUpdateRequest("De wacht", "About the night watch", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31), null, null, null))).isEmpty();
    }

    @Test
    void blankName_isRejected() {
        assertThat(violated(new CampaignUpdateRequest(" ", null, null, null, null, null, null)))
                .containsExactly("name: " + CampaignConstant.NAME_REQUIRED_MESSAGE);
    }
}
