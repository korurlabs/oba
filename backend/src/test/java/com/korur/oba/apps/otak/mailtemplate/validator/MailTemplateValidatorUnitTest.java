package com.korur.oba.apps.otak.mailtemplate.validator;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.mailtemplate.entity.MailTemplate;
import com.korur.oba.apps.otak.mailtemplate.repository.MailTemplateRepository;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class MailTemplateValidatorUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID mailTemplateId = UUID.randomUUID();

    @Mock
    private MailTemplateRepository mailTemplateRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private MailTemplateValidator validator;

    @Nested
    class EnsureMailTemplateExists {

        @Test
        void mailTemplateOfOrganization_returnsIt() {
            MailTemplate mailTemplate = MailTemplate.builder().id(mailTemplateId).organizationId(organizationId).build();
            when(mailTemplateRepository.findByIdAndOrganizationId(mailTemplateId, organizationId))
                    .thenReturn(Optional.of(mailTemplate));

            assertThat(validator.ensureMailTemplateExists(organizationId, mailTemplateId)).isSameAs(mailTemplate);
        }

        @Test
        void mailTemplateMissingOrOfAnotherOrganization_throwsNotFound() {
            when(mailTemplateRepository.findByIdAndOrganizationId(mailTemplateId, organizationId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.ensureMailTemplateExists(organizationId, mailTemplateId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_MAIL_TEMPLATE_FOUND.getCode()));
        }
    }

    @Nested
    class EnsureNameFree {

        @Test
        void createWithFreeName_passesAndUsesTheCheckWithoutExclusion() {
            when(mailTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguage(
                    organizationId, "Intro", DocumentLanguage.NL)).thenReturn(false);

            assertThatCode(() -> validator.ensureNameFree(organizationId, "Intro", DocumentLanguage.NL, null))
                    .doesNotThrowAnyException();
            verify(mailTemplateRepository, never()).existsByOrganizationIdAndNameIgnoreCaseAndLanguageAndIdNot(
                    organizationId, "Intro", DocumentLanguage.NL, null);
        }

        @Test
        void createWithTakenName_throwsNameTaken() {
            when(mailTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguage(
                    organizationId, "Intro", DocumentLanguage.NL)).thenReturn(true);

            assertThatThrownBy(() -> validator.ensureNameFree(organizationId, "Intro", DocumentLanguage.NL, null))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_MAIL_TEMPLATE_NAME_TAKEN.getCode()));
        }

        @Test
        void updateKeepingItsOwnName_passesBecauseItsOwnRowIsExcluded() {
            when(mailTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguageAndIdNot(
                    organizationId, "Intro", DocumentLanguage.EN, mailTemplateId)).thenReturn(false);

            assertThatCode(() -> validator.ensureNameFree(organizationId, "Intro", DocumentLanguage.EN, mailTemplateId))
                    .doesNotThrowAnyException();
            verify(mailTemplateRepository, never()).existsByOrganizationIdAndNameIgnoreCaseAndLanguage(
                    organizationId, "Intro", DocumentLanguage.EN);
        }

        @Test
        void updateToTheNameOfAnotherMailTemplate_throwsNameTaken() {
            when(mailTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguageAndIdNot(
                    organizationId, "Intro", DocumentLanguage.EN, mailTemplateId)).thenReturn(true);

            assertThatThrownBy(() -> validator.ensureNameFree(organizationId, "Intro", DocumentLanguage.EN,
                    mailTemplateId)).isInstanceOf(ConflictException.class);
        }
    }
}
