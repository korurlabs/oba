package com.korur.oba.apps.otak.outreach.validator;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.mail.dto.request.MailRequest;
import com.korur.oba.apps.otak.outreach.constant.OutreachConstant;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachCreateRequest;
import com.korur.oba.apps.otak.outreach.entity.Outreach;
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;
import com.korur.oba.apps.otak.outreach.enums.OutreachType;
import com.korur.oba.apps.otak.outreach.repository.OutreachRepository;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class OutreachValidatorUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID relationId = UUID.randomUUID();

    @Mock
    private OutreachRepository outreachRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private OutreachValidator validator;

    private Outreach outreach(OutreachStatus status) {
        return Outreach.builder().id(UUID.randomUUID()).status(status).organizationId(organizationId)
                .relationId(relationId).build();
    }

    private OutreachCreateRequest request(UUID mailTemplateId, MailRequest mail) {
        return new OutreachCreateRequest(relationId, UUID.randomUUID(), "Title", OutreachType.MAIL, mailTemplateId, mail, List.of(), null);
    }

    private void mailFitsType(OutreachCreateRequest request) {
        validator.ensureMailFitsType(request.type(), request.mailTemplateId(), request.mail(), request.documentTemplateIds());
    }

    @Nested
    class EnsureOutreachExists {

        @Test
        void outreachOfOrganization_returnsIt() {
            Outreach outreach = outreach(OutreachStatus.DRAFT);
            when(outreachRepository.findByIdAndOrganizationId(outreach.getId(), organizationId))
                    .thenReturn(Optional.of(outreach));

            assertThat(validator.ensureOutreachExists(organizationId, outreach.getId())).isSameAs(outreach);
        }

        @Test
        void outreachMissingOrOfAnotherOrganization_throwsNotFound() {
            UUID outreachId = UUID.randomUUID();
            when(outreachRepository.findByIdAndOrganizationId(outreachId, organizationId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.ensureOutreachExists(organizationId, outreachId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_OUTREACH_FOUND.getCode()));
        }
    }

    @Nested
    class EnsureEditable {

        @ParameterizedTest
        @EnumSource(value = OutreachStatus.class, names = {"DRAFT", "FAILED"})
        void draftOrFailed_passes(OutreachStatus status) {
            assertThatCode(() -> validator.ensureEditable(outreach(status))).doesNotThrowAnyException();
        }

        @Test
        void sent_throwsNotEditableConflict() {
            assertThatThrownBy(() -> validator.ensureEditable(outreach(OutreachStatus.SENT)))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_OUTREACH_NOT_EDITABLE.getCode()));
        }
    }

    @Nested
    class EnsureMailFitsType {

        @Test
        void onlyATemplate_passes() {
            assertThatCode(() -> mailFitsType(request(UUID.randomUUID(), null)))
                    .doesNotThrowAnyException();
        }

        @Test
        void onlyAMail_passes() {
            assertThatCode(() -> mailFitsType(
                    request(null, new MailRequest("S", "B", null)))).doesNotThrowAnyException();
        }

        @Test
        void both_throwsGenericValidation() {
            assertThatThrownBy(() -> mailFitsType(
                    request(UUID.randomUUID(), new MailRequest("S", "B", null))))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionValidationError.VALIDATION.getCode()));
        }

        @Test
        void neither_throwsGenericValidation() {
            assertThatThrownBy(() -> mailFitsType(request(null, null)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionValidationError.VALIDATION.getCode()));
        }
    }

    @Nested
    class EnsureHasContact {

        @Test
        void outreachWithContact_returnsItsId() {
            UUID contactId = UUID.randomUUID();
            Outreach outreach = Outreach.builder().id(UUID.randomUUID()).contactId(contactId).build();

            assertThat(validator.ensureHasContact(outreach)).isEqualTo(contactId);
        }

        @Test
        void outreachWhoseContactWasDeleted_throwsContactNotFound() {
            Outreach outreach = Outreach.builder().id(UUID.randomUUID()).organizationId(organizationId).build();

            assertThatThrownBy(() -> validator.ensureHasContact(outreach))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_CONTACT_FOUND.getCode()));
        }
    }

    @Nested
    class EnsureContactAddress {

        @Test
        void addressWithSpaces_returnsItTrimmed() {
            Contact contact = Contact.builder().id(UUID.randomUUID()).email("  jan@fabrikam.example ").build();

            assertThat(validator.ensureContactAddress(outreach(OutreachStatus.DRAFT), contact))
                    .isEqualTo("jan@fabrikam.example");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void blankAddress_throwsWithoutAddress(String email) {
            Contact contact = Contact.builder().id(UUID.randomUUID()).email(email).build();

            assertThatThrownBy(() -> validator.ensureContactAddress(outreach(OutreachStatus.DRAFT), contact))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_CONTACT_WITHOUT_ADDRESS.getCode()));
        }

        @Test
        void nullAddress_throwsWithoutAddress() {
            Contact contact = Contact.builder().id(UUID.randomUUID()).build();

            assertThatThrownBy(() -> validator.ensureContactAddress(outreach(OutreachStatus.DRAFT), contact))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class EnsureAttachmentsFit {

        @Test
        void exactlyThreeMebibytes_passes() {
            assertThatCode(() -> validator.ensureAttachmentsFit(outreach(OutreachStatus.DRAFT),
                    OutreachConstant.ATTACHMENTS_MAX_BYTES)).doesNotThrowAnyException();
        }

        @Test
        void oneByteOverThreeMebibytes_throwsTooLarge() {
            assertThatThrownBy(() -> validator.ensureAttachmentsFit(outreach(OutreachStatus.DRAFT),
                    OutreachConstant.ATTACHMENTS_MAX_BYTES + 1))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_ATTACHMENTS_TOO_LARGE.getCode()));
        }
    }

    @Nested
    class EnsureClaimed {

        @Test
        void oneRow_passes() {
            assertThatCode(() -> validator.ensureClaimed(outreach(OutreachStatus.DRAFT), 1))
                    .doesNotThrowAnyException();
        }

        @Test
        void zeroRows_throwsNotEditableConflict() {
            assertThatThrownBy(() -> validator.ensureClaimed(outreach(OutreachStatus.DRAFT), 0))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_OUTREACH_NOT_EDITABLE.getCode()));
        }
    }

    @Nested
    class EnsureMailFitsTypeOfAChannel {

        private Map<String, Object> refusedFields(ValidationException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(ExceptionValidationError.VALIDATION.getCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) exception.getErrorValues();
            return details;
        }

        @ParameterizedTest
        @EnumSource(value = OutreachType.class, names = {"WHATSAPP", "CALL"})
        void noMailFields_passes(OutreachType type) {
            assertThatCode(() -> validator.ensureMailFitsType(type, null, null, null)).doesNotThrowAnyException();
            assertThatCode(() -> validator.ensureMailFitsType(type, null, null, List.of()))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = OutreachType.class, names = {"WHATSAPP", "CALL"})
        void mailTemplateId_isRefusedOnThatField(OutreachType type) {
            assertThatThrownBy(() -> validator.ensureMailFitsType(type, UUID.randomUUID(), null, null))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(refusedFields(exception)).containsOnlyKeys(OutreachConstant.FIELD_MAIL_TEMPLATE_ID));
        }

        @ParameterizedTest
        @EnumSource(value = OutreachType.class, names = {"WHATSAPP", "CALL"})
        void mail_isRefusedOnThatField(OutreachType type) {
            assertThatThrownBy(() -> validator.ensureMailFitsType(type, null, new MailRequest("S", "B", null), null))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(refusedFields(exception)).containsOnlyKeys(OutreachConstant.FIELD_MAIL));
        }

        @ParameterizedTest
        @EnumSource(value = OutreachType.class, names = {"WHATSAPP", "CALL"})
        void documentTemplateIds_areRefusedOnThatField(OutreachType type) {
            assertThatThrownBy(() -> validator.ensureMailFitsType(type, null, null, List.of(UUID.randomUUID())))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(refusedFields(exception)).containsOnlyKeys(OutreachConstant.FIELD_DOCUMENT_TEMPLATE_IDS));
        }

        @Test
        void everyRefusedField_isNamedAtOnce() {
            assertThatThrownBy(() -> validator.ensureMailFitsType(OutreachType.CALL, UUID.randomUUID(),
                    new MailRequest("S", "B", null), List.of(UUID.randomUUID())))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(refusedFields(exception)).containsOnlyKeys(OutreachConstant.FIELD_MAIL_TEMPLATE_ID,
                                    OutreachConstant.FIELD_MAIL, OutreachConstant.FIELD_DOCUMENT_TEMPLATE_IDS));
        }
    }

    @Nested
    class EnsureUpdateMailFitsType {

        @Test
        void mailOutreachWithMail_passes() {
            assertThatCode(() -> validator.ensureUpdateMailFitsType(OutreachType.MAIL, new MailRequest("S", "B", null)))
                    .doesNotThrowAnyException();
        }

        @Test
        void mailOutreachWithoutMail_throwsGenericValidation() {
            assertThatThrownBy(() -> validator.ensureUpdateMailFitsType(OutreachType.MAIL, null))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionValidationError.VALIDATION.getCode()));
        }

        @ParameterizedTest
        @EnumSource(value = OutreachType.class, names = {"WHATSAPP", "CALL"})
        void channelWithoutMail_passes(OutreachType type) {
            assertThatCode(() -> validator.ensureUpdateMailFitsType(type, null)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = OutreachType.class, names = {"WHATSAPP", "CALL"})
        void channelWithMail_throwsGenericValidation(OutreachType type) {
            assertThatThrownBy(() -> validator.ensureUpdateMailFitsType(type, new MailRequest("S", "B", null)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionValidationError.VALIDATION.getCode()));
        }
    }

    @Nested
    class EnsureTypeAndNotType {

        private Outreach ofType(OutreachType type) {
            Outreach outreach = outreach(OutreachStatus.DRAFT);
            outreach.setType(type);
            return outreach;
        }

        @Test
        void ensureType_sameType_passes() {
            assertThatCode(() -> validator.ensureType(ofType(OutreachType.MAIL), OutreachType.MAIL))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = OutreachType.class, names = {"WHATSAPP", "CALL"})
        void ensureType_otherType_throwsActionNotForType(OutreachType type) {
            assertThatThrownBy(() -> validator.ensureType(ofType(type), OutreachType.MAIL))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_ACTION_NOT_FOR_TYPE.getCode()));
        }

        @Test
        void ensureNotType_refusedType_throwsActionNotForType() {
            assertThatThrownBy(() -> validator.ensureNotType(ofType(OutreachType.MAIL), OutreachType.MAIL))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_ACTION_NOT_FOR_TYPE.getCode()));
        }

        @ParameterizedTest
        @EnumSource(value = OutreachType.class, names = {"WHATSAPP", "CALL"})
        void ensureNotType_otherType_passes(OutreachType type) {
            assertThatCode(() -> validator.ensureNotType(ofType(type), OutreachType.MAIL)).doesNotThrowAnyException();
        }
    }

    @Nested
    class EnsureContactPhone {

        @Test
        void phoneWithSpaces_returnsItTrimmed() {
            Contact contact = Contact.builder().id(UUID.randomUUID()).phone("  +31 6 1234 5678 ").build();

            assertThat(validator.ensureContactPhone(outreach(OutreachStatus.DRAFT), contact))
                    .isEqualTo("+31 6 1234 5678");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void blankPhone_throwsWithoutPhone(String phone) {
            Contact contact = Contact.builder().id(UUID.randomUUID()).phone(phone).build();

            assertThatThrownBy(() -> validator.ensureContactPhone(outreach(OutreachStatus.DRAFT), contact))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_CONTACT_WITHOUT_PHONE.getCode()));
        }

        @Test
        void nullPhone_throwsWithoutPhone() {
            Contact contact = Contact.builder().id(UUID.randomUUID()).build();

            assertThatThrownBy(() -> validator.ensureContactPhone(outreach(OutreachStatus.DRAFT), contact))
                    .isInstanceOf(ValidationException.class);
        }
    }
}
