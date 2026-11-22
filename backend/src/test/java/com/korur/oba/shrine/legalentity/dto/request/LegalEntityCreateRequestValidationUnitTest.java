package com.korur.oba.shrine.legalentity.dto.request;

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
import java.util.Set;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.shrine.legalentity.constant.LegalEntityConstant;

class LegalEntityCreateRequestValidationUnitTest {

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

    private static Set<String> violationMessages(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void validRequestWithOnlyRequiredFields_hasNoViolation() {
        LegalEntityCreateRequest request = new LegalEntityCreateRequest("Korur B.V.", "Korur B.V.", "NLD",
                null, null, null, null, null, null, null);

        assertThat(violationMessages(request)).isEmpty();
    }

    @Test
    void blankRequiredFields_reportEachMessage() {
        LegalEntityCreateRequest request = new LegalEntityCreateRequest(" ", null, "",
                null, null, null, null, null, null, null);

        assertThat(violationMessages(request)).contains(
                LegalEntityConstant.SHORT_NAME_REQUIRED_MESSAGE,
                LegalEntityConstant.LEGAL_NAME_REQUIRED_MESSAGE,
                LegalEntityConstant.COUNTRY_REQUIRED_MESSAGE);
    }

    @Test
    void blankOptionalFields_reportEachMessage() {
        LegalEntityCreateRequest request = new LegalEntityCreateRequest("Korur B.V.", "Korur B.V.", "NLD",
                " ", " ", " ", " ", " ", " ", " ");

        assertThat(violationMessages(request)).containsExactlyInAnyOrder(
                LegalEntityConstant.REGISTRATION_NUMBER_BLANK_MESSAGE,
                LegalEntityConstant.VAT_NUMBER_BLANK_MESSAGE,
                LegalEntityConstant.STREET_BLANK_MESSAGE,
                LegalEntityConstant.POSTAL_CODE_BLANK_MESSAGE,
                LegalEntityConstant.CITY_BLANK_MESSAGE,
                LegalEntityConstant.GOVERNING_LAW_BLANK_MESSAGE,
                LegalEntityConstant.COURT_BLANK_MESSAGE);
    }

    @Test
    void valuesLongerThanTheirColumns_reportEachMessage() {
        LegalEntityCreateRequest request = new LegalEntityCreateRequest("a".repeat(101), "a".repeat(201), "NLDX",
                "a".repeat(51), "a".repeat(51), "a".repeat(201), "a".repeat(21), "a".repeat(101), "a".repeat(201),
                "a".repeat(201));

        assertThat(violationMessages(request)).containsExactlyInAnyOrder(
                LegalEntityConstant.SHORT_NAME_SIZE_MESSAGE,
                LegalEntityConstant.LEGAL_NAME_SIZE_MESSAGE,
                LegalEntityConstant.COUNTRY_SIZE_MESSAGE,
                LegalEntityConstant.REGISTRATION_NUMBER_SIZE_MESSAGE,
                LegalEntityConstant.VAT_NUMBER_SIZE_MESSAGE,
                LegalEntityConstant.STREET_SIZE_MESSAGE,
                LegalEntityConstant.POSTAL_CODE_SIZE_MESSAGE,
                LegalEntityConstant.CITY_SIZE_MESSAGE,
                LegalEntityConstant.GOVERNING_LAW_SIZE_MESSAGE,
                LegalEntityConstant.COURT_SIZE_MESSAGE);
    }
}
