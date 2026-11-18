package com.korur.oba.apps.otak.event.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.korur.oba.apps.otak.support.RequestValidationSupport.violations;
import static com.korur.oba.apps.otak.support.RequestValidationSupport.with;
import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.time.LocalDate;

// 4. Internal
import com.korur.oba.apps.otak.event.constant.EventConstant;
import com.korur.oba.apps.otak.event.enums.EventType;

class EventUpdateRequestValidationUnitTest {

    private static EventUpdateRequest valid() {
        return new EventUpdateRequest("IGEF 2026", EventType.EXPO, LocalDate.of(2026, 11, 19), null, null, null, null,
                null);
    }

    @Test
    void validBody_hasNoViolation() {
        assertThat(violations(valid())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void name_blank_isRequired(String blank) {
        assertThat(violations(with(valid(), "name", blank)))
                .containsExactly("name: " + EventConstant.NAME_REQUIRED_MESSAGE);
    }

    @Test
    void typeAndStartDate_omitted_areRequired() {
        assertThat(violations(with(with(valid(), "type", null), "startDate", null))).containsExactlyInAnyOrder(
                "type: " + EventConstant.TYPE_REQUIRED_MESSAGE,
                "startDate: " + EventConstant.START_DATE_REQUIRED_MESSAGE);
    }

    @Test
    void nullableTexts_null_areValid() {
        assertThat(violations(with(with(with(valid(), "city", null), "website", null), "notes", null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void nullableTexts_blank_areRejectedWithTheirBlankMessage(String blank) {
        assertThat(violations(with(with(with(valid(), "city", blank), "website", blank), "notes", blank)))
                .contains(
                        "city: " + EventConstant.CITY_BLANK_MESSAGE,
                        "website: " + EventConstant.WEBSITE_BLANK_MESSAGE,
                        "notes: " + EventConstant.NOTES_BLANK_MESSAGE);
    }

    @Test
    void texts_overTheirLimit_areRejectedBySize() {
        assertThat(violations(with(with(valid(), "name", "a".repeat(EventConstant.NAME_MAX_LENGTH + 1)), "city",
                "c".repeat(EventConstant.CITY_MAX_LENGTH + 1)))).containsExactlyInAnyOrder(
                "name: " + EventConstant.NAME_SIZE_MESSAGE, "city: " + EventConstant.CITY_SIZE_MESSAGE);
    }

    @Test
    void website_withoutScheme_isRejectedByPattern() {
        assertThat(violations(with(valid(), "website", "igef.com.tr")))
                .containsExactly("website: " + EventConstant.WEBSITE_PATTERN_MESSAGE);
    }
}
