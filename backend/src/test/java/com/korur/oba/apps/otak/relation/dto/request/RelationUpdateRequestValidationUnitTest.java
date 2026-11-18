package com.korur.oba.apps.otak.relation.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.korur.oba.apps.otak.support.RequestValidationSupport.violations;
import static com.korur.oba.apps.otak.support.RequestValidationSupport.with;
import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.math.BigDecimal;

// 4. Internal
import com.korur.oba.apps.otak.relation.constant.RelationConstant;

class RelationUpdateRequestValidationUnitTest {

    private static final String[] NULLABLE_TEXT_FIELDS = {"sector", "registrationNumber", "vatNumber", "street",
            "postalCode", "city", "notes", "website"};

    private static RelationUpdateRequest valid() {
        return new RelationUpdateRequest("Fabrikam", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void validBody_hasNoViolation() {
        assertThat(violations(valid())).isEmpty();
    }

    @Test
    void nullableTexts_null_areValid() {
        RelationUpdateRequest request = valid();
        for (String field : NULLABLE_TEXT_FIELDS) {
            request = with(request, field, null);
        }
        assertThat(violations(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void nullableTexts_blank_areRejectedWithTheirBlankMessage(String blank) {
        RelationUpdateRequest request = valid();
        for (String field : NULLABLE_TEXT_FIELDS) {
            request = with(request, field, blank);
        }
        assertThat(violations(request)).contains(
                "website: " + RelationConstant.WEBSITE_BLANK_MESSAGE,
                "sector: " + RelationConstant.SECTOR_BLANK_MESSAGE,
                "registrationNumber: " + RelationConstant.REGISTRATION_NUMBER_BLANK_MESSAGE,
                "vatNumber: " + RelationConstant.VAT_NUMBER_BLANK_MESSAGE,
                "street: " + RelationConstant.STREET_BLANK_MESSAGE,
                "postalCode: " + RelationConstant.POSTAL_CODE_BLANK_MESSAGE,
                "city: " + RelationConstant.CITY_BLANK_MESSAGE,
                "notes: " + RelationConstant.NOTES_BLANK_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void name_blank_isRequired(String blank) {
        assertThat(violations(with(valid(), "name", blank)))
                .containsExactly("name: " + RelationConstant.NAME_REQUIRED_MESSAGE);
    }

    @Test
    void name_null_isRequired() {
        assertThat(violations(with(valid(), "name", null)))
                .containsExactly("name: " + RelationConstant.NAME_REQUIRED_MESSAGE);
    }

    @Test
    void texts_overTheirLimit_areRejectedBySize() {
        RelationUpdateRequest request = with(valid(), "name", "a".repeat(RelationConstant.NAME_MAX_LENGTH + 1));
        request = with(request, "sector", "a".repeat(RelationConstant.SECTOR_MAX_LENGTH + 1));
        request = with(request, "registrationNumber", "a".repeat(RelationConstant.REGISTRATION_NUMBER_MAX_LENGTH + 1));
        request = with(request, "vatNumber", "a".repeat(RelationConstant.VAT_NUMBER_MAX_LENGTH + 1));
        request = with(request, "street", "a".repeat(RelationConstant.STREET_MAX_LENGTH + 1));
        request = with(request, "postalCode", "a".repeat(RelationConstant.POSTAL_CODE_MAX_LENGTH + 1));
        request = with(request, "city", "a".repeat(RelationConstant.CITY_MAX_LENGTH + 1));
        request = with(request, "notes", "a".repeat(RelationConstant.NOTES_MAX_LENGTH + 1));

        assertThat(violations(request)).containsExactlyInAnyOrder(
                "name: " + RelationConstant.NAME_SIZE_MESSAGE,
                "sector: " + RelationConstant.SECTOR_SIZE_MESSAGE,
                "registrationNumber: " + RelationConstant.REGISTRATION_NUMBER_SIZE_MESSAGE,
                "vatNumber: " + RelationConstant.VAT_NUMBER_SIZE_MESSAGE,
                "street: " + RelationConstant.STREET_SIZE_MESSAGE,
                "postalCode: " + RelationConstant.POSTAL_CODE_SIZE_MESSAGE,
                "city: " + RelationConstant.CITY_SIZE_MESSAGE,
                "notes: " + RelationConstant.NOTES_SIZE_MESSAGE);
    }

    @Test
    void website_atLimitWithScheme_isValidAndOverLimitIsRejected() {
        String prefix = "https://fabrikam.example/";
        String atLimit = prefix + "a".repeat(RelationConstant.WEBSITE_MAX_LENGTH - prefix.length());

        assertThat(violations(with(valid(), "website", atLimit))).isEmpty();
        assertThat(violations(with(valid(), "website", atLimit + "a")))
                .containsExactly("website: " + RelationConstant.WEBSITE_SIZE_MESSAGE);
    }

    @Test
    void website_withoutScheme_isRejectedByPattern() {
        assertThat(violations(with(valid(), "website", "fabrikam.example")))
                .containsExactly("website: " + RelationConstant.WEBSITE_PATTERN_MESSAGE);
    }

    @Test
    void expectedValue_negativeOrTooManyDecimals_isRejected() {
        assertThat(violations(with(valid(), "expectedValue", new BigDecimal("-1"))))
                .containsExactly("expectedValue: " + RelationConstant.EXPECTED_VALUE_MIN_MESSAGE);
        assertThat(violations(with(valid(), "expectedValue", new BigDecimal("1.234"))))
                .containsExactly("expectedValue: " + RelationConstant.EXPECTED_VALUE_DIGITS_MESSAGE);
        assertThat(violations(with(valid(), "expectedValue", new BigDecimal("1250.50")))).isEmpty();
    }
}
