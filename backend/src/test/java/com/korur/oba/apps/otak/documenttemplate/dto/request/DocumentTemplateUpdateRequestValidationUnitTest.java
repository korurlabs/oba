package com.korur.oba.apps.otak.documenttemplate.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.korur.oba.apps.otak.support.RequestValidationSupport.violations;
import static com.korur.oba.apps.otak.support.RequestValidationSupport.with;
import static org.assertj.core.api.Assertions.assertThat;

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentType;
import com.korur.oba.apps.otak.documenttemplate.constant.DocumentTemplateConstant;

class DocumentTemplateUpdateRequestValidationUnitTest {

    private static DocumentTemplateUpdateRequest valid() {
        return new DocumentTemplateUpdateRequest(DocumentType.PROPOSAL, "First contact", DocumentLanguage.NL);
    }

    @Test
    void validBody_hasNoViolation() {
        assertThat(violations(valid())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void name_blank_isRequired(String blank) {
        assertThat(violations(with(valid(), "name", blank))).containsExactly("name: " + DocumentTemplateConstant.NAME_REQUIRED_MESSAGE);
    }

    @Test
    void typeAndLanguage_omitted_areRequired() {
        assertThat(violations(with(with(valid(), "type", null), "language", null))).containsExactlyInAnyOrder(
                "type: " + DocumentTemplateConstant.TYPE_REQUIRED_MESSAGE, "language: " + DocumentTemplateConstant.LANGUAGE_REQUIRED_MESSAGE);
    }
}
