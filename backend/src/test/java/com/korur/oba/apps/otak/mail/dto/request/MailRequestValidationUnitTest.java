package com.korur.oba.apps.otak.mail.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static com.korur.oba.apps.otak.support.RequestValidationSupport.violations;
import static com.korur.oba.apps.otak.support.RequestValidationSupport.with;
import static org.assertj.core.api.Assertions.assertThat;

// 4. Internal
import com.korur.oba.apps.otak.mail.constant.MailConstant;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

class MailRequestValidationUnitTest {

    private static MailRequest valid() {
        return new MailRequest("Subject", "Body", DocumentLanguage.NL);
    }

    @Test
    void validBody_hasNoViolation() {
        assertThat(violations(valid())).isEmpty();
    }

    @Test
    void language_omitted_isRequired() {
        assertThat(violations(with(valid(), "language", null)))
                .containsExactly("language: " + MailConstant.LANGUAGE_REQUIRED_MESSAGE);
    }

    @Test
    void language_everyValue_isValid() {
        for (DocumentLanguage language : DocumentLanguage.values()) {
            assertThat(violations(with(valid(), "language", language))).isEmpty();
        }
    }

    @Test
    void subjectAndBody_blank_areRequired() {
        assertThat(violations(with(with(valid(), "subject", "  "), "body", ""))).containsExactlyInAnyOrder(
                "subject: " + MailConstant.SUBJECT_REQUIRED_MESSAGE, "body: " + MailConstant.BODY_REQUIRED_MESSAGE);
    }

    @Test
    void subjectAndBody_overTheirLimit_areRejectedBySize() {
        assertThat(violations(with(with(valid(), "subject", "s".repeat(MailConstant.SUBJECT_MAX_LENGTH + 1)), "body",
                "b".repeat(MailConstant.BODY_MAX_LENGTH + 1)))).containsExactlyInAnyOrder(
                "subject: " + MailConstant.SUBJECT_SIZE_MESSAGE, "body: " + MailConstant.BODY_SIZE_MESSAGE);
    }
}
