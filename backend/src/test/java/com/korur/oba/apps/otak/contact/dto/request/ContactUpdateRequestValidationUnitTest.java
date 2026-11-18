package com.korur.oba.apps.otak.contact.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.korur.oba.apps.otak.support.RequestValidationSupport.violations;
import static com.korur.oba.apps.otak.support.RequestValidationSupport.with;
import static org.assertj.core.api.Assertions.assertThat;

// 4. Internal
import com.korur.oba.apps.otak.contact.constant.ContactConstant;
import com.korur.oba.apps.otak.contact.enums.ContactKind;

class ContactUpdateRequestValidationUnitTest {

    private static ContactUpdateRequest valid() {
        return new ContactUpdateRequest("Jan de Vries", ContactKind.PERSON, null, null, null, null, null, false, false, false);
    }

    @Test
    void validBody_hasNoViolation() {
        assertThat(violations(valid())).isEmpty();
    }

    @Test
    void nullableTexts_null_areValid() {
        ContactUpdateRequest request = valid();
        for (String field : new String[]{"role", "email", "phone", "linkedinUrl"}) {
            request = with(request, field, null);
        }
        assertThat(violations(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void nullableTexts_blank_areRejectedWithTheirBlankMessage(String blank) {
        ContactUpdateRequest request = valid();
        for (String field : new String[]{"role", "email", "phone", "linkedinUrl"}) {
            request = with(request, field, blank);
        }
        assertThat(violations(request)).contains(
                "role: " + ContactConstant.ROLE_BLANK_MESSAGE,
                "email: " + ContactConstant.EMAIL_BLANK_MESSAGE,
                "phone: " + ContactConstant.PHONE_BLANK_MESSAGE,
                "linkedinUrl: " + ContactConstant.LINKEDIN_URL_BLANK_MESSAGE);
    }

    @Test
    void kindAndEveryFlag_omitted_areRequired() {
        ContactUpdateRequest request = with(with(with(with(valid(), "kind", null), "decisionMaker", null),
                "mainPersonContact", null), "mainBusinessContact", null);

        assertThat(violations(request)).containsExactlyInAnyOrder(
                "kind: " + ContactConstant.KIND_REQUIRED_MESSAGE,
                "decisionMaker: " + ContactConstant.DECISION_MAKER_REQUIRED_MESSAGE,
                "mainPersonContact: " + ContactConstant.MAIN_PERSON_CONTACT_REQUIRED_MESSAGE,
                "mainBusinessContact: " + ContactConstant.MAIN_BUSINESS_CONTACT_REQUIRED_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void name_blank_isRequired(String blank) {
        assertThat(violations(with(valid(), "name", blank)))
                .containsExactly("name: " + ContactConstant.NAME_REQUIRED_MESSAGE);
    }

    @Test
    void texts_overTheirLimit_areRejectedBySize() {
        ContactUpdateRequest request = with(valid(), "name", "a".repeat(ContactConstant.NAME_MAX_LENGTH + 1));
        request = with(request, "role", "a".repeat(ContactConstant.ROLE_MAX_LENGTH + 1));
        request = with(request, "phone", "1".repeat(ContactConstant.PHONE_MAX_LENGTH + 1));

        assertThat(violations(request)).containsExactlyInAnyOrder(
                "name: " + ContactConstant.NAME_SIZE_MESSAGE,
                "role: " + ContactConstant.ROLE_SIZE_MESSAGE,
                "phone: " + ContactConstant.PHONE_SIZE_MESSAGE);
    }

    @Test
    void email_notAnAddress_isRejectedByFormat() {
        assertThat(violations(with(valid(), "email", "not-an-address")))
                .containsExactly("email: " + ContactConstant.EMAIL_FORMAT_MESSAGE);
        assertThat(violations(with(valid(), "email", "jan@fabrikam.example"))).isEmpty();
    }

    @Test
    void linkedinUrl_withoutSchemeOrOverLimit_isRejected() {
        String prefix = "https://linkedin.com/in/";
        String tooLong = prefix + "a".repeat(ContactConstant.LINKEDIN_URL_MAX_LENGTH + 1 - prefix.length());

        assertThat(violations(with(valid(), "linkedinUrl", "linkedin.com/in/jan")))
                .containsExactly("linkedinUrl: " + ContactConstant.LINKEDIN_URL_PATTERN_MESSAGE);
        assertThat(violations(with(valid(), "linkedinUrl", tooLong)))
                .containsExactly("linkedinUrl: " + ContactConstant.LINKEDIN_URL_SIZE_MESSAGE);
        assertThat(violations(with(valid(), "linkedinUrl", prefix + "jan"))).isEmpty();
    }
}
