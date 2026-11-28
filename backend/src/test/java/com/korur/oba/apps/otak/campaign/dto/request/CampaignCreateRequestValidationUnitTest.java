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

class CampaignCreateRequestValidationUnitTest {

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

    private static CampaignCreateRequest request(String name, String description) {
        return new CampaignCreateRequest(name, description, null, null, null, null, null);
    }

    @Test
    void onlyName_isValid() {
        assertThat(violated(request("De wacht", null))).isEmpty();
    }

    @Test
    void blankName_isRejected() {
        assertThat(violated(request("  ", null))).containsExactly("name: " + CampaignConstant.NAME_REQUIRED_MESSAGE);
        assertThat(violated(request(null, null))).containsExactly("name: " + CampaignConstant.NAME_REQUIRED_MESSAGE);
    }

    @Test
    void longNameAndDescription_areAcceptedWithoutSizeLimit() {
        assertThat(violated(request("a".repeat(5000), "b".repeat(50000)))).isEmpty();
    }

    @Test
    void endDateBeforeStartDate_isLeftToTheService() {
        CampaignCreateRequest request = new CampaignCreateRequest("De wacht", null, LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 12, 1), null, null, null);

        assertThat(violated(request)).isEmpty();
    }

    @Test
    void blankSearch_isRejectedAndAbsentOrLongIsValid() {
        CampaignQueryRequest blank = new CampaignQueryRequest();
        blank.setSearch(" ");
        CampaignQueryRequest longSearch = new CampaignQueryRequest();
        longSearch.setSearch("w".repeat(500));

        assertThat(violated(blank)).containsExactly("search: " + CampaignConstant.SEARCH_BLANK_MESSAGE);
        assertThat(violated(new CampaignQueryRequest())).isEmpty();
        assertThat(violated(longSearch)).isEmpty();
    }
}
