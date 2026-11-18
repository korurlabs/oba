package com.korur.oba.apps.otak.documenttemplate.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;

import static com.korur.oba.apps.otak.support.RequestValidationSupport.violations;
import static com.korur.oba.apps.otak.support.RequestValidationSupport.with;
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
import com.korur.oba.apps.otak.documenttemplate.constant.DocumentTemplateConstant;
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentType;

class DocumentTemplateCreateRequestValidationUnitTest {

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
    void validRequest_hasNoViolation() {
        DocumentTemplateCreateRequest request = new DocumentTemplateCreateRequest(
                DocumentType.PROPOSAL, "First contact", DocumentLanguage.NL);

        assertThat(violationMessages(request)).isEmpty();
    }

    @Test
    void missingTypeAndLanguageAndBlankName_reportsEachMessage() {
        DocumentTemplateCreateRequest request = new DocumentTemplateCreateRequest(null, " ", null);

        assertThat(violationMessages(request)).containsExactlyInAnyOrder(
                DocumentTemplateConstant.TYPE_REQUIRED_MESSAGE,
                DocumentTemplateConstant.NAME_REQUIRED_MESSAGE,
                DocumentTemplateConstant.LANGUAGE_REQUIRED_MESSAGE);
    }

    @Test
    void nameOf101Characters_reportsTheSizeMessage() {
        DocumentTemplateCreateRequest request = new DocumentTemplateCreateRequest(
                DocumentType.NDA, "a".repeat(DocumentTemplateConstant.NAME_MAX_LENGTH + 1), DocumentLanguage.EN);

        assertThat(violationMessages(request)).containsExactly(DocumentTemplateConstant.NAME_SIZE_MESSAGE);
    }

    @Test
    void nameOf100Characters_hasNoViolation() {
        DocumentTemplateCreateRequest request = new DocumentTemplateCreateRequest(
                DocumentType.NDA, "a".repeat(DocumentTemplateConstant.NAME_MAX_LENGTH), DocumentLanguage.EN);

        assertThat(violationMessages(request)).isEmpty();
    }

    @Test
    void queryRequestSearchOf101Characters_reportsTheSizeMessage() {
        DocumentTemplateQueryRequest request = new DocumentTemplateQueryRequest();
        request.setSearch("a".repeat(DocumentTemplateConstant.SEARCH_MAX_LENGTH + 1));

        assertThat(violationMessages(request)).containsExactly(DocumentTemplateConstant.SEARCH_SIZE_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void create_name_blank_isRequired(String blank) {
        assertThat(violations(with(validCreate(), "name", blank))).containsExactly("name: " + DocumentTemplateConstant.NAME_REQUIRED_MESSAGE);
    }

    @Test
    void create_typeAndLanguage_omitted_areRequired() {
        assertThat(violations(with(with(validCreate(), "type", null), "language", null))).containsExactlyInAnyOrder(
                "type: " + DocumentTemplateConstant.TYPE_REQUIRED_MESSAGE, "language: " + DocumentTemplateConstant.LANGUAGE_REQUIRED_MESSAGE);
    }

    @Test
    void querySearch_blankIsRejectedAndAbsentIsValid() {
        DocumentTemplateQueryRequest blank = new DocumentTemplateQueryRequest();
        blank.setSearch("  ");

        assertThat(violations(blank)).containsExactly("search: " + DocumentTemplateConstant.SEARCH_BLANK_MESSAGE);
        assertThat(violations(new DocumentTemplateQueryRequest())).isEmpty();
    }

    private static DocumentTemplateCreateRequest validCreate() {
        return new DocumentTemplateCreateRequest(DocumentType.PROPOSAL, "First contact", DocumentLanguage.NL);
    }
}
