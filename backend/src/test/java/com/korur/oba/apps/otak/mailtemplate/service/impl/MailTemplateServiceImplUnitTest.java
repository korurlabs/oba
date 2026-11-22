package com.korur.oba.apps.otak.mailtemplate.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.mailtemplate.dto.request.MailTemplateCreateRequest;
import com.korur.oba.apps.otak.mailtemplate.dto.request.MailTemplateUpdateRequest;
import com.korur.oba.apps.otak.mailtemplate.dto.response.MailTemplateDetailResponse;
import com.korur.oba.apps.otak.mailtemplate.entity.MailTemplate;
import com.korur.oba.apps.otak.mailtemplate.mapper.MailTemplateMapperImpl;
import com.korur.oba.apps.otak.mailtemplate.repository.MailTemplateRepository;
import com.korur.oba.apps.otak.mailtemplate.validator.MailTemplateValidator;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class MailTemplateServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID mailTemplateId = UUID.randomUUID();

    @Mock
    private MailTemplateRepository mailTemplateRepository;

    @Mock
    private LoggingService loggingService;

    private MailTemplateServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        // The real validator and mapper run over the mocked repository, so the error codes and the
        // mapped fields are the production ones.
        service = new MailTemplateServiceImpl(mailTemplateRepository, new MailTemplateMapperImpl(),
                new MailTemplateValidator(mailTemplateRepository, loggingService), loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    private MailTemplate stored() {
        return MailTemplate.builder().id(mailTemplateId).organizationId(organizationId).name("Old")
                .language(DocumentLanguage.NL).subject("Old subject").body("Old body").build();
    }

    @Nested
    class Create {

        @Test
        void validInput_savesTheTrimmedNameAndTheOrganizationOfTheHeader() {
            // Arrange
            when(mailTemplateRepository.save(any(MailTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));
            MailTemplateCreateRequest request = new MailTemplateCreateRequest("  Intro  ", DocumentLanguage.EN,
                    "Subject", "Body");

            // Act
            MailTemplateDetailResponse result = service.create(organizationId, user, request);

            // Assert
            ArgumentCaptor<MailTemplate> captor = ArgumentCaptor.forClass(MailTemplate.class);
            verify(mailTemplateRepository).save(captor.capture());
            MailTemplate saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo("Intro");
            assertThat(saved.getLanguage()).isEqualTo(DocumentLanguage.EN);
            assertThat(saved.getSubject()).isEqualTo("Subject");
            assertThat(saved.getBody()).isEqualTo("Body");
            assertThat(saved.getOrganizationId()).isEqualTo(organizationId);
            assertThat(result.name()).isEqualTo("Intro");
            assertThat(result.body()).isEqualTo("Body");
        }

        @Test
        void nameTakenAfterTrimming_throwsConflictAndSavesNothing() {
            // Arrange
            when(mailTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguage(
                    organizationId, "Intro", DocumentLanguage.NL)).thenReturn(true);
            MailTemplateCreateRequest request = new MailTemplateCreateRequest(" Intro ", DocumentLanguage.NL,
                    "Subject", "Body");

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, request))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_MAIL_TEMPLATE_NAME_TAKEN.getCode()));
            verify(mailTemplateRepository, never()).save(any());
        }
    }

    @Nested
    class Update {

        @Test
        void mailTemplateExists_replacesEveryField() {
            // Arrange
            MailTemplate mailTemplate = stored();
            when(mailTemplateRepository.findByIdAndOrganizationId(mailTemplateId, organizationId))
                    .thenReturn(Optional.of(mailTemplate));
            when(mailTemplateRepository.saveAndFlush(mailTemplate)).thenReturn(mailTemplate);
            MailTemplateUpdateRequest request = new MailTemplateUpdateRequest(" New ", DocumentLanguage.EN,
                    "New subject", "New body");

            // Act
            MailTemplateDetailResponse result = service.update(organizationId, mailTemplateId, request);

            // Assert
            assertThat(mailTemplate.getName()).isEqualTo("New");
            assertThat(mailTemplate.getLanguage()).isEqualTo(DocumentLanguage.EN);
            assertThat(mailTemplate.getSubject()).isEqualTo("New subject");
            assertThat(mailTemplate.getBody()).isEqualTo("New body");
            assertThat(result.subject()).isEqualTo("New subject");
        }

        @Test
        void mailTemplateOfAnotherOrganization_throwsNotFoundAndWritesNothing() {
            // Arrange
            when(mailTemplateRepository.findByIdAndOrganizationId(mailTemplateId, organizationId))
                    .thenReturn(Optional.empty());
            MailTemplateUpdateRequest request = new MailTemplateUpdateRequest("New", DocumentLanguage.NL, "S", "B");

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, mailTemplateId, request))
                    .isInstanceOf(NotFoundException.class);
            verify(mailTemplateRepository, never()).saveAndFlush(any());
        }

        @Test
        void nameOfAnotherMailTemplate_throwsConflictAndKeepsTheStoredFields() {
            // Arrange
            MailTemplate mailTemplate = stored();
            when(mailTemplateRepository.findByIdAndOrganizationId(mailTemplateId, organizationId))
                    .thenReturn(Optional.of(mailTemplate));
            when(mailTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguageAndIdNot(
                    organizationId, "Taken", DocumentLanguage.NL, mailTemplateId)).thenReturn(true);
            MailTemplateUpdateRequest request = new MailTemplateUpdateRequest("Taken", DocumentLanguage.NL, "S", "B");

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, mailTemplateId, request))
                    .isInstanceOf(ConflictException.class);
            assertThat(mailTemplate.getName()).isEqualTo("Old");
            verify(mailTemplateRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class Delete {

        @Test
        void mailTemplateExists_deletesIt() {
            // Arrange
            MailTemplate mailTemplate = stored();
            when(mailTemplateRepository.findByIdAndOrganizationId(mailTemplateId, organizationId))
                    .thenReturn(Optional.of(mailTemplate));

            // Act
            service.delete(organizationId, mailTemplateId);

            // Assert
            verify(mailTemplateRepository).delete(mailTemplate);
        }

        @Test
        void mailTemplateOfAnotherOrganization_throwsNotFoundAndDeletesNothing() {
            // Arrange
            when(mailTemplateRepository.findByIdAndOrganizationId(mailTemplateId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, mailTemplateId))
                    .isInstanceOf(NotFoundException.class);
            verify(mailTemplateRepository, never()).delete(any(MailTemplate.class));
        }
    }
}
