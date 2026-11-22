package com.korur.oba.apps.otak.mail.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.mail.dto.request.MailRequest;
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.mapper.MailMapperImpl;
import com.korur.oba.apps.otak.mail.repository.MailRepository;
import com.korur.oba.apps.otak.mail.validator.MailValidator;
import com.korur.oba.apps.otak.mailtemplate.entity.MailTemplate;
import com.korur.oba.apps.otak.mailtemplate.service.MailTemplateQueryService;
import com.korur.oba.dojo.exception.enums.ExceptionNotFoundError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

@ExtendWith(MockitoExtension.class)
class MailServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID relationId = UUID.randomUUID();
    private final UUID mailId = UUID.randomUUID();

    @Mock
    private MailRepository mailRepository;

    @Mock
    private MailTemplateQueryService mailTemplateQueryService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private LoggingService loggingService;

    private MailServiceImpl service;

    @BeforeEach
    void setUp() {
        // The real mapper and validator run over the mocked repository and query services.
        service = new MailServiceImpl(mailRepository, new MailMapperImpl(),
                new MailValidator(mailRepository, loggingService), mailTemplateQueryService, attachmentService,
                loggingService);
    }

    private Mail storedMail() {
        return Mail.builder().id(mailId).organizationId(organizationId).relationId(relationId).subject("Old")
                .body("Old body").language(DocumentLanguage.NL).build();
    }

    private void echoesSavedMail() {
        when(mailRepository.save(any(Mail.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    class CreateFromTemplate {

        @Test
        void mailTemplateOfOrganization_copiesSubjectBodyLanguageAndRecordsTheTemplate() {
            // Arrange
            echoesSavedMail();
            UUID mailTemplateId = UUID.randomUUID();
            MailTemplate template = MailTemplate.builder().id(mailTemplateId).organizationId(organizationId)
                    .name("Intro").subject("Template subject").body("Template body").language(DocumentLanguage.EN)
                    .build();
            when(mailTemplateQueryService.getEntity(organizationId, mailTemplateId)).thenReturn(template);

            // Act
            Mail result = service.createFromTemplate(organizationId, relationId, mailTemplateId);

            // Assert
            assertThat(result.getSubject()).isEqualTo("Template subject");
            assertThat(result.getBody()).isEqualTo("Template body");
            assertThat(result.getLanguage()).isEqualTo(DocumentLanguage.EN);
            assertThat(result.getMailTemplateId()).isEqualTo(mailTemplateId);
            assertThat(result.getOrganizationId()).isEqualTo(organizationId);
            assertThat(result.getRelationId()).isEqualTo(relationId);
            assertThat(result.getId()).isNotEqualTo(mailTemplateId);
        }

        @Test
        void mailTemplateNotFound_throwsAndSavesNothing() {
            // Arrange
            UUID mailTemplateId = UUID.randomUUID();
            NotFoundException notFound = new NotFoundException(ExceptionDomainOtakError.NOT_MAIL_TEMPLATE_FOUND,
                    java.util.Map.of());
            when(mailTemplateQueryService.getEntity(organizationId, mailTemplateId)).thenThrow(notFound);

            // Act and assert
            assertThatThrownBy(() -> service.createFromTemplate(organizationId, relationId, mailTemplateId))
                    .isSameAs(notFound);
            verify(mailRepository, never()).save(any());
        }
    }

    @Nested
    class Create {

        @Test
        void request_savesAMailWithoutTemplateLinkForTheRelation() {
            // Arrange
            echoesSavedMail();

            // Act
            Mail result = service.create(organizationId, relationId,
                    new MailRequest("Subject", "Body", DocumentLanguage.NL));

            // Assert
            assertThat(result.getSubject()).isEqualTo("Subject");
            assertThat(result.getBody()).isEqualTo("Body");
            assertThat(result.getLanguage()).isEqualTo(DocumentLanguage.NL);
            assertThat(result.getMailTemplateId()).isNull();
            assertThat(result.getOrganizationId()).isEqualTo(organizationId);
            assertThat(result.getRelationId()).isEqualTo(relationId);
        }
    }

    @Nested
    class Update {

        @Test
        void mailExists_writesTheContentAndKeepsTheOwnersAndTheTemplateLink() {
            // Arrange
            Mail mail = storedMail();
            UUID templateLink = UUID.randomUUID();
            mail.setMailTemplateId(templateLink);
            when(mailRepository.findByIdAndOrganizationId(mailId, organizationId)).thenReturn(Optional.of(mail));

            // Act
            service.update(organizationId, mailId, new MailRequest("New", "New body", DocumentLanguage.EN));

            // Assert
            assertThat(mail.getSubject()).isEqualTo("New");
            assertThat(mail.getBody()).isEqualTo("New body");
            assertThat(mail.getLanguage()).isEqualTo(DocumentLanguage.EN);
            assertThat(mail.getMailTemplateId()).isEqualTo(templateLink);
            assertThat(mail.getRelationId()).isEqualTo(relationId);
            verify(mailRepository).saveAndFlush(mail);
        }

        @Test
        void mailOfAnotherOrganization_throwsGenericNotFoundAndWritesNothing() {
            // Arrange
            when(mailRepository.findByIdAndOrganizationId(mailId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, mailId,
                    new MailRequest("S", "B", DocumentLanguage.NL)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionNotFoundError.RESOURCE_FOUND.getCode()));
            verify(mailRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class Delete {

        @Test
        void mailExists_deletesItThenItsAttachments() {
            // Arrange
            Mail mail = storedMail();
            when(mailRepository.findByIdAndOrganizationId(mailId, organizationId)).thenReturn(Optional.of(mail));

            // Act
            service.delete(organizationId, mailId);

            // Assert
            ArgumentCaptor<Mail> captor = ArgumentCaptor.forClass(Mail.class);
            InOrder order = inOrder(mailRepository, attachmentService);
            order.verify(mailRepository).delete(captor.capture());
            order.verify(attachmentService).deleteAttachmentsByEntity(StorageEntityType.MAIL, mailId);
            assertThat(captor.getValue()).isSameAs(mail);
        }

        @Test
        void mailOfAnotherOrganization_throwsNotFoundAndDeletesNothing() {
            // Arrange
            when(mailRepository.findByIdAndOrganizationId(mailId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, mailId)).isInstanceOf(NotFoundException.class);
            verify(mailRepository, never()).delete(any(Mail.class));
            verify(attachmentService, never()).deleteAttachmentsByEntity(any(), any());
        }
    }
}
