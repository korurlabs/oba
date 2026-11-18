package com.korur.oba.apps.otak.event.dto.request;

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
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.otak.event.constant.EventConstant;
import com.korur.oba.apps.otak.event.enums.EventType;

class EventCreateRequestValidationUnitTest {

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

    private static EventCreateRequest valid() {
        return new EventCreateRequest("IGEF 2026", EventType.EXPO, LocalDate.of(2026, 11, 19), null, null, null, null, null);
    }

    private static Set<String> violated(EventCreateRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.toSet());
    }

    private static Set<String> violated(EventQueryRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.toSet());
    }

    @Test
    void onlyRequiredFields_isValid() {
        assertThat(violated(valid())).isEmpty();
    }

    @Test
    void blankName_isRejected() {
        EventCreateRequest request = new EventCreateRequest("  ", EventType.EXPO, LocalDate.of(2026, 11, 19), null, null, null,
                null, null);
        assertThat(violated(request)).containsExactly("name: " + EventConstant.NAME_REQUIRED_MESSAGE);
    }

    @Test
    void nameOf201Characters_isRejectedAnd200IsValid() {
        EventCreateRequest tooLong = new EventCreateRequest("a".repeat(201), EventType.EXPO, LocalDate.of(2026, 11, 19), null,
                null, null, null, null);
        EventCreateRequest atLimit = new EventCreateRequest("a".repeat(200), EventType.EXPO, LocalDate.of(2026, 11, 19), null,
                null, null, null, null);
        assertThat(violated(tooLong)).containsExactly("name: " + EventConstant.NAME_SIZE_MESSAGE);
        assertThat(violated(atLimit)).isEmpty();
    }

    @Test
    void missingTypeAndStartDate_areRejected() {
        EventCreateRequest request = new EventCreateRequest("IGEF", null, null, null, null, null, null, null);
        assertThat(violated(request)).containsExactlyInAnyOrder(
                "type: " + EventConstant.TYPE_REQUIRED_MESSAGE,
                "startDate: " + EventConstant.START_DATE_REQUIRED_MESSAGE);
    }

    @Test
    void cityOf101Characters_isRejected() {
        EventCreateRequest request = new EventCreateRequest("IGEF", EventType.EXPO, LocalDate.of(2026, 11, 19), null,
                "c".repeat(101), null, null, null);
        assertThat(violated(request)).containsExactly("city: " + EventConstant.CITY_SIZE_MESSAGE);
    }

    @Test
    void website_httpsPage_isValid() {
        EventCreateRequest request = new EventCreateRequest("IGEF", EventType.EXPO, LocalDate.of(2026, 11, 19), null, null, null,
                "https://igef.com.tr/", null);
        assertThat(violated(request)).isEmpty();
    }

    @Test
    void website_withoutScheme_isRejectedByPattern() {
        EventCreateRequest request = new EventCreateRequest("IGEF", EventType.EXPO, LocalDate.of(2026, 11, 19), null, null, null,
                "igef.com.tr", null);
        assertThat(violated(request)).containsExactly("website: " + EventConstant.WEBSITE_PATTERN_MESSAGE);
    }

    @Test
    void website_over500Characters_isRejected() {
        String website = "https://igef.com.tr/" + "a".repeat(481);
        EventCreateRequest request = new EventCreateRequest("IGEF", EventType.EXPO, LocalDate.of(2026, 11, 19), null, null, null,
                website, null);
        assertThat(violated(request)).containsExactly("website: " + EventConstant.WEBSITE_SIZE_MESSAGE);
    }

    @Test
    void notes_emptyIsRejectedNullAndTextAreValid() {
        LocalDate start = LocalDate.of(2026, 11, 19);
        assertThat(violated(new EventCreateRequest("IGEF", EventType.EXPO, start, null, null, null, null, "")))
                .containsExactly("notes: " + EventConstant.NOTES_BLANK_MESSAGE);
        assertThat(violated(new EventCreateRequest("IGEF", EventType.EXPO, start, null, null, null, null, "Hall 4")))
                .isEmpty();
    }

    @Test
    void notesOf10001Characters_isRejected() {
        EventCreateRequest request = new EventCreateRequest("IGEF", EventType.EXPO, LocalDate.of(2026, 11, 19), null, null, null,
                null, "n".repeat(10001));
        assertThat(violated(request)).containsExactly("notes: " + EventConstant.NOTES_SIZE_MESSAGE);
    }

    @Test
    void querySearchOf201Characters_isRejected() {
        EventQueryRequest request = new EventQueryRequest();
        request.setSearch("s".repeat(201));
        assertThat(violated(request)).containsExactly("search: " + EventConstant.SEARCH_SIZE_MESSAGE);
    }

    @Test
    void city_blank_isRejectedWithItsBlankMessage() {
        EventCreateRequest request = new EventCreateRequest("IGEF", EventType.EXPO, LocalDate.of(2026, 11, 19), null, "  ",
                null, null, null);
        assertThat(violated(request)).containsExactly("city: " + EventConstant.CITY_BLANK_MESSAGE);
    }

    @Test
    void website_blank_isRejectedWithItsBlankMessage() {
        EventCreateRequest request = new EventCreateRequest("IGEF", EventType.EXPO, LocalDate.of(2026, 11, 19), null, null,
                null, "", null);
        assertThat(violated(request)).contains("website: " + EventConstant.WEBSITE_BLANK_MESSAGE);
    }

    @Test
    void querySearch_blankIsRejectedAndAbsentIsValid() {
        EventQueryRequest blank = new EventQueryRequest();
        blank.setSearch("   ");
        assertThat(violated(blank)).containsExactly("search: " + EventConstant.SEARCH_BLANK_MESSAGE);
        assertThat(violated(new EventQueryRequest())).isEmpty();
    }
}
