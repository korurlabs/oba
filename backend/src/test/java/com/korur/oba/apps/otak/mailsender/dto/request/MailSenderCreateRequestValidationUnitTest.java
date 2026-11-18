package com.korur.oba.apps.otak.mailsender.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

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
import com.korur.oba.apps.otak.mailsender.constant.MailSenderConstant;
import com.korur.oba.apps.otak.mailsender.enums.EmailType;

class MailSenderCreateRequestValidationUnitTest {

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

    private static Set<String> violationMessages(MailSenderCreateRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void validMailAddress_hasNoViolation() {
        assertThat(validator.validate(new MailSenderCreateRequest(EmailType.PERSONAL, "jane.doe@example.com"))).isEmpty();
    }

    @Test
    void mailAddressAtItsMaximumLength_hasNoViolation() {
        // 64 characters is the longest local part and 63 the longest domain label a mail address may have.
        String domain = "@" + "d".repeat(60) + "." + "e".repeat(60) + "." + "f".repeat(63) + ".com";
        String mailAddress = "l".repeat(MailSenderConstant.MAIL_ADDRESS_MAX_LENGTH - domain.length()) + domain;

        assertThat(mailAddress).hasSize(MailSenderConstant.MAIL_ADDRESS_MAX_LENGTH);
        assertThat(validator.validate(new MailSenderCreateRequest(EmailType.PERSONAL, mailAddress))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void blankMailAddress_violatesNotBlank(String mailAddress) {
        assertThat(violationMessages(new MailSenderCreateRequest(EmailType.PERSONAL, mailAddress)))
                .contains(MailSenderConstant.MAIL_ADDRESS_REQUIRED_MESSAGE);
    }

    @Test
    void nullMailAddress_violatesNotBlank() {
        assertThat(violationMessages(new MailSenderCreateRequest(EmailType.PERSONAL, null)))
                .containsExactly(MailSenderConstant.MAIL_ADDRESS_REQUIRED_MESSAGE);
    }

    @Test
    void malformedMailAddress_violatesEmail() {
        assertThat(violationMessages(new MailSenderCreateRequest(EmailType.PERSONAL, "not-a-mail-address")))
                .containsExactly(MailSenderConstant.MAIL_ADDRESS_FORMAT_MESSAGE);
    }

    @Test
    void mailAddressOverItsMaximumLength_violatesSize() {
        String mailAddress = "a".repeat(MailSenderConstant.MAIL_ADDRESS_MAX_LENGTH) + "@example.com";

        assertThat(violationMessages(new MailSenderCreateRequest(EmailType.PERSONAL, mailAddress)))
                .contains(MailSenderConstant.MAIL_ADDRESS_SIZE_MESSAGE);
    }

    @Test
    void nullType_violatesNotNullWithTypeRequiredMessage() {
        assertThat(violationMessages(new MailSenderCreateRequest(null, "jane.doe@example.com")))
                .containsExactly("Type is required");
        assertThat(MailSenderConstant.TYPE_REQUIRED_MESSAGE).isEqualTo("Type is required");
    }

    @ParameterizedTest
    @EnumSource(EmailType.class)
    void everyType_hasNoViolation(EmailType type) {
        assertThat(validator.validate(new MailSenderCreateRequest(type, "jane.doe@example.com"))).isEmpty();
    }
}
