package com.korur.oba.apps.otak.outreach.dto.request;

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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.mail.constant.MailConstant;
import com.korur.oba.apps.otak.mail.dto.request.MailRequest;
import com.korur.oba.apps.otak.outreach.constant.OutreachConstant;
import com.korur.oba.apps.otak.outreach.enums.OutreachType;

class OutreachCreateRequestValidationUnitTest {

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
    void validRequestWithMail_hasNoViolation() {
        OutreachCreateRequest request = new OutreachCreateRequest(UUID.randomUUID(), UUID.randomUUID(), "Title", OutreachType.MAIL, null,
                new MailRequest("S", "B", DocumentLanguage.NL), List.of(UUID.randomUUID()));

        assertThat(violationMessages(request)).isEmpty();
    }

    @Test
    void missingRelationContactAndBlankTitle_reportsEachRequiredMessage() {
        OutreachCreateRequest request = new OutreachCreateRequest(null, null, " ", OutreachType.MAIL, UUID.randomUUID(), null, null);

        assertThat(violationMessages(request)).containsExactlyInAnyOrder(
                OutreachConstant.RELATION_REQUIRED_MESSAGE, OutreachConstant.CONTACT_REQUIRED_MESSAGE,
                OutreachConstant.TITLE_REQUIRED_MESSAGE);
    }

    @Test
    void titleOf201Characters_reportsTheSizeMessage() {
        OutreachCreateRequest request = new OutreachCreateRequest(UUID.randomUUID(), UUID.randomUUID(),
                "t".repeat(OutreachConstant.TITLE_MAX_LENGTH + 1), OutreachType.MAIL, UUID.randomUUID(), null, null);

        assertThat(violationMessages(request)).containsExactly(OutreachConstant.TITLE_SIZE_MESSAGE);
    }

    @Test
    void invalidNestedMail_isReported() {
        OutreachCreateRequest request = new OutreachCreateRequest(UUID.randomUUID(), UUID.randomUUID(), "Title", OutreachType.MAIL, null,
                new MailRequest(" ", "", DocumentLanguage.NL), null);

        assertThat(violationMessages(request)).containsExactlyInAnyOrder(
                MailConstant.SUBJECT_REQUIRED_MESSAGE, MailConstant.BODY_REQUIRED_MESSAGE);
    }

    @Test
    void nullDocumentTemplateId_isReported() {
        OutreachCreateRequest request = new OutreachCreateRequest(UUID.randomUUID(), UUID.randomUUID(), "Title", OutreachType.MAIL,
                UUID.randomUUID(), null, Arrays.asList(UUID.randomUUID(), null));

        assertThat(violationMessages(request)).containsExactly(OutreachConstant.DOCUMENT_TEMPLATE_ID_REQUIRED_MESSAGE);
    }

    @Test
    void mailTooLong_reportsBothSizeMessages() {
        MailRequest mail = new MailRequest("s".repeat(MailConstant.SUBJECT_MAX_LENGTH + 1),
                "b".repeat(MailConstant.BODY_MAX_LENGTH + 1), DocumentLanguage.NL);

        assertThat(violationMessages(mail)).containsExactlyInAnyOrder(
                MailConstant.SUBJECT_SIZE_MESSAGE, MailConstant.BODY_SIZE_MESSAGE);
    }

    @Test
    void updateRequestWithoutContactAndTitle_reportsTheRequiredMessagesButAllowsANullMail() {
        OutreachUpdateRequest request = new OutreachUpdateRequest(null, "", null);

        assertThat(violationMessages(request)).containsExactlyInAnyOrder(
                OutreachConstant.CONTACT_REQUIRED_MESSAGE, OutreachConstant.TITLE_REQUIRED_MESSAGE);
    }

    @Test
    void sendRequestWithoutSender_reportsTheRequiredMessage() {
        assertThat(violationMessages(new OutreachSendRequest(null)))
                .containsExactly(OutreachConstant.MAIL_SENDER_REQUIRED_MESSAGE);
    }
}
