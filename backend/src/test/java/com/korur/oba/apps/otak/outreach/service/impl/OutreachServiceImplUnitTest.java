package com.korur.oba.apps.otak.outreach.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.contact.validator.ContactValidator;
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.documenttemplate.service.DocumentTemplateService;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.mail.dto.request.MailRequest;
import com.korur.oba.apps.otak.mail.service.MailQueryService;
import com.korur.oba.apps.otak.mail.service.MailService;
import com.korur.oba.apps.otak.mailsender.entity.MailSender;
import com.korur.oba.apps.otak.mailsender.enums.EmailType;
import com.korur.oba.apps.otak.mailsender.validator.MailSenderValidator;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachCreateRequest;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachSendRequest;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachUpdateRequest;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachDetailResponse;
import com.korur.oba.apps.otak.outreach.entity.Outreach;
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;
import com.korur.oba.apps.otak.outreach.enums.OutreachType;
import com.korur.oba.apps.otak.outreach.mapper.OutreachMapperImpl;
import com.korur.oba.apps.otak.outreach.repository.OutreachRepository;
import com.korur.oba.apps.otak.outreach.service.OutreachQueryService;
import com.korur.oba.apps.otak.outreach.validator.OutreachValidator;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.notification.client.MsGraphClient;
import com.korur.oba.connect.notification.dto.AttachmentEmail;
import com.korur.oba.connect.notification.dto.GraphMailbox;
import com.korur.oba.connect.notification.dto.Mail;
import com.korur.oba.connect.notification.exception.ExceptionDomainNotificationError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.exception.internal.infrastructure.IntegrationErrorException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentContent;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;

@ExtendWith(MockitoExtension.class)
class OutreachServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    private final UUID organizationId = UUID.randomUUID();
    private final UUID relationId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();
    private final UUID outreachId = UUID.randomUUID();
    private final UUID mailId = UUID.randomUUID();

    @Mock
    private OutreachRepository outreachRepository;

    @Mock
    private OutreachQueryService outreachQueryService;

    @Mock
    private RelationQueryService relationQueryService;

    @Mock
    private ContactValidator contactValidator;

    @Mock
    private MailService mailService;

    @Mock
    private MailQueryService mailQueryService;

    @Mock
    private DocumentTemplateService documentTemplateService;

    @Mock
    private AttachmentQueryService attachmentQueryService;

    @Mock
    private MailSenderValidator mailSenderValidator;

    @Mock
    private MsGraphClient msGraphClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private LoggingService loggingService;

    private OutreachServiceImpl service;
    private User user;
    private OutreachDetailResponse detail;

    @BeforeEach
    void setUp() {
        // The real validators and mapper run over the mocked collaborators; the transaction template is
        // real over a mocked manager, so the claim and the failure marker run their callbacks.
        service = new OutreachServiceImpl(outreachRepository, new OutreachMapperImpl(),
                new OutreachValidator(outreachRepository, loggingService), outreachQueryService,
                relationQueryService, contactValidator, mailService, mailQueryService,
                documentTemplateService, attachmentQueryService, mailSenderValidator, msGraphClient,
                new TransactionTemplate(transactionManager), Clock.fixed(NOW, ZoneOffset.UTC), loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
        detail = mock(OutreachDetailResponse.class);
    }

    private Outreach outreach(OutreachStatus status) {
        return Outreach.builder().id(outreachId).title("Title").type(OutreachType.MAIL).status(status)
                .organizationId(organizationId).relationId(relationId).contactId(contactId)
                .mailId(mailId).build();
    }

    /** The outreach's mail; the entity shares its simple name with the Graph message, so it is written in full. */
    private com.korur.oba.apps.otak.mail.entity.Mail mail() {
        return com.korur.oba.apps.otak.mail.entity.Mail.builder().id(mailId).subject("Hello")
                .body("Line <1>\nLine 2\r\nLine 3").language(DocumentLanguage.NL).organizationId(organizationId)
                .relationId(relationId).build();
    }

    private void lockedFinds(Outreach outreach) {
        when(outreachRepository.findByIdAndOrganizationIdForUpdate(outreach.getId(), organizationId))
                .thenReturn(Optional.of(outreach));
    }

    @Nested
    class Create {

        private OutreachCreateRequest withMail(List<UUID> documentTemplateIds) {
            return new OutreachCreateRequest(relationId, contactId, "  Title  ", OutreachType.MAIL, null,
                    new MailRequest("Subject", "Body", DocumentLanguage.NL), documentTemplateIds);
        }

        private void savesWithAnId() {
            when(outreachRepository.saveAndFlush(any(Outreach.class))).thenAnswer(invocation -> {
                Outreach saved = invocation.getArgument(0);
                saved.setId(outreachId);
                return saved;
            });
            when(outreachQueryService.get(organizationId, outreachId)).thenReturn(detail);
        }

        private Outreach savedOutreach() {
            ArgumentCaptor<Outreach> captor = ArgumentCaptor.forClass(Outreach.class);
            verify(outreachRepository).saveAndFlush(captor.capture());
            return captor.getValue();
        }

        @Test
        void ownMail_writesTheMailThenSavesADraftMailOutreachLinkedToIt() {
            // Arrange
            savesWithAnId();
            OutreachCreateRequest request = withMail(null);
            when(mailService.create(organizationId, relationId, request.mail())).thenReturn(mail());

            // Act
            OutreachDetailResponse result = service.create(organizationId, user, request);

            // Assert
            Outreach saved = savedOutreach();
            assertThat(saved.getMailId()).isEqualTo(mailId);
            assertThat(saved.getTitle()).isEqualTo("Title");
            assertThat(saved.getType()).isEqualTo(OutreachType.MAIL);
            assertThat(saved.getStatus()).isEqualTo(OutreachStatus.DRAFT);
            assertThat(saved.getOrganizationId()).isEqualTo(organizationId);
            verify(mailService, never()).createFromTemplate(any(), any(), any());
            assertThat(result).isSameAs(detail);
        }

        @Test
        void mailTemplate_copiesTheTemplateIntoTheOutreachsMail() {
            // Arrange
            savesWithAnId();
            UUID mailTemplateId = UUID.randomUUID();
            OutreachCreateRequest request = new OutreachCreateRequest(relationId, contactId, "Title", OutreachType.MAIL,
                    mailTemplateId, null, null);
            when(mailService.createFromTemplate(organizationId, relationId, mailTemplateId)).thenReturn(mail());

            // Act
            service.create(organizationId, user, request);

            // Assert
            assertThat(savedOutreach().getMailId()).isEqualTo(mailId);
            verify(mailService, never()).create(any(), any(), any());
        }

        @Test
        void documentTemplates_areCopiedOntoTheMailAfterTheOutreachIsSavedEachOnceInOrder() {
            // Arrange
            savesWithAnId();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            OutreachCreateRequest request = withMail(List.of(first, second, first));
            when(mailService.create(organizationId, relationId, request.mail())).thenReturn(mail());

            // Act
            service.create(organizationId, user, request);

            // Assert
            InOrder order = inOrder(mailService, outreachRepository, documentTemplateService);
            order.verify(mailService).create(organizationId, relationId, request.mail());
            order.verify(outreachRepository).saveAndFlush(any(Outreach.class));
            order.verify(documentTemplateService).copyToAttachment(user, organizationId, first,
                    StorageEntityType.MAIL, mailId);
            order.verify(documentTemplateService).copyToAttachment(user, organizationId, second,
                    StorageEntityType.MAIL, mailId);
            order.verifyNoMoreInteractions();
        }

        @Test
        void bothMailSources_throwsBeforeCreatingAnything() {
            // Arrange
            OutreachCreateRequest request = new OutreachCreateRequest(relationId, contactId, "Title", OutreachType.MAIL, UUID.randomUUID(),
                    new MailRequest("S", "B", null), null);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, request))
                    .isInstanceOf(ValidationException.class);
            verifyNoInteractions(mailService);
            verify(outreachRepository, never()).saveAndFlush(any());
        }

        @Test
        void contactOfAnotherRelation_throwsNotFoundBeforeCreatingAnything() {
            // Arrange
            NotFoundException notFound = new NotFoundException(ExceptionDomainOtakError.NOT_CONTACT_FOUND, Map.of());
            when(contactValidator.ensureContactExists(organizationId, relationId, contactId)).thenThrow(notFound);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, withMail(null))).isSameAs(notFound);
            verifyNoInteractions(mailService);
            verify(outreachRepository, never()).saveAndFlush(any());
        }

        @Test
        void documentTemplateRefused_throwsTheCauseAndReadsNothingBack() {
            // Arrange
            when(outreachRepository.saveAndFlush(any(Outreach.class))).thenAnswer(invocation -> invocation.getArgument(0));
            UUID templateId = UUID.randomUUID();
            OutreachCreateRequest request = withMail(List.of(templateId));
            when(mailService.create(organizationId, relationId, request.mail())).thenReturn(mail());
            ValidationException notUsable = new ValidationException(ExceptionDomainOtakError.VAL_DOCUMENT_NOT_USABLE,
                    Map.of());
            doThrow(notUsable).when(documentTemplateService).copyToAttachment(user, organizationId, templateId,
                    StorageEntityType.MAIL, mailId);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, request)).isSameAs(notUsable);
            verify(outreachQueryService, never()).get(any(), any());
        }
    }

    @Nested
    class Update {

        private OutreachUpdateRequest request() {
            return new OutreachUpdateRequest(contactId, "  Renamed  ", new MailRequest("S2", "B2", DocumentLanguage.EN));
        }

        @Test
        void draft_writesTheMailAndTheTrimmedTitle() {
            // Arrange
            Outreach outreach = outreach(OutreachStatus.DRAFT);
            lockedFinds(outreach);
            OutreachUpdateRequest request = request();
            when(outreachQueryService.get(organizationId, outreachId)).thenReturn(detail);

            // Act
            OutreachDetailResponse result = service.update(organizationId, outreachId, request);

            // Assert
            verify(mailService).update(organizationId, mailId, request.mail());
            assertThat(outreach.getTitle()).isEqualTo("Renamed");
            assertThat(outreach.getMailId()).isEqualTo(mailId);
            verify(outreachRepository).saveAndFlush(outreach);
            assertThat(result).isSameAs(detail);
        }

        @Test
        void sent_throwsNotEditableAndChangesNothing() {
            // Arrange
            lockedFinds(outreach(OutreachStatus.SENT));

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, outreachId, request()))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_OUTREACH_NOT_EDITABLE.getCode()));
            verifyNoInteractions(mailService);
            verify(outreachRepository, never()).saveAndFlush(any());
        }

        @Test
        void outreachOfAnotherOrganization_throwsNotFound() {
            // Arrange
            when(outreachRepository.findByIdAndOrganizationIdForUpdate(outreachId, organizationId))
                    .thenReturn(Optional.empty());
            when(outreachRepository.findByIdAndOrganizationId(outreachId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, outreachId, request()))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_OUTREACH_FOUND.getCode()));
        }
    }

    @Nested
    class Delete {

        @Test
        void draft_deletesTheOutreachThenItsMail() {
            // Arrange
            Outreach outreach = outreach(OutreachStatus.DRAFT);
            lockedFinds(outreach);

            // Act
            service.delete(organizationId, outreachId);

            // Assert
            InOrder order = inOrder(outreachRepository, mailService);
            order.verify(outreachRepository).delete(outreach);
            order.verify(outreachRepository).flush();
            order.verify(mailService).delete(organizationId, mailId);
        }

        @Test
        void outreachWithoutMail_deletesOnlyTheOutreach() {
            // Arrange
            Outreach outreach = outreach(OutreachStatus.DRAFT);
            outreach.setMailId(null);
            lockedFinds(outreach);

            // Act
            service.delete(organizationId, outreachId);

            // Assert
            verify(outreachRepository).delete(outreach);
            verifyNoInteractions(mailService);
        }

        @Test
        void sent_throwsNotEditableAndDeletesNothing() {
            // Arrange
            lockedFinds(outreach(OutreachStatus.SENT));

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, outreachId))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_OUTREACH_NOT_EDITABLE.getCode()));
            verify(outreachRepository, never()).delete(any(Outreach.class));
            verifyNoInteractions(mailService);
        }

        @Test
        void outreachOfAnotherOrganization_throwsNotFound() {
            // Arrange
            when(outreachRepository.findByIdAndOrganizationIdForUpdate(outreachId, organizationId))
                    .thenReturn(Optional.empty());
            when(outreachRepository.findByIdAndOrganizationId(outreachId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, outreachId))
                    .isInstanceOf(NotFoundException.class);
            verifyNoInteractions(mailService);
        }
    }

    @Nested
    class Send {

        private final UUID senderId = UUID.randomUUID();
        private final OutreachSendRequest request = new OutreachSendRequest(senderId);

        private MailSender sender(EmailType type, String address) {
            return MailSender.builder().id(senderId).type(type).mailAddress(address)
                    .organizationId(organizationId).build();
        }

        /** A DRAFT outreach, a contact with an address and a PERSONAL sender, every precondition met. */
        private Outreach readyToSend() {
            Outreach outreach = outreach(OutreachStatus.DRAFT);
            when(outreachRepository.findByIdAndOrganizationId(outreachId, organizationId))
                    .thenReturn(Optional.of(outreach));
            when(mailSenderValidator.ensureMailSenderExists(organizationId, senderId))
                    .thenReturn(sender(EmailType.PERSONAL, "sales@example.com"));
            when(contactValidator.ensureContactExists(organizationId, relationId, contactId))
                    .thenReturn(Contact.builder().id(contactId).email(" jan@fabrikam.example ").build());
            return outreach;
        }

        private void claimSucceeds() {
            when(outreachRepository.claimForSending(outreachId, organizationId, senderId, "jan@fabrikam.example", NOW))
                    .thenReturn(1);
        }

        private Mail sentMail() {
            ArgumentCaptor<Mail> captor = ArgumentCaptor.forClass(Mail.class);
            verify(msGraphClient).sendEmail(captor.capture());
            return captor.getValue();
        }

        @Test
        void personalSender_claimsThenSendsFromTheStoredAddressToTheTrimmedContactAddress() {
            // Arrange
            readyToSend();
            claimSucceeds();
            when(mailQueryService.getEntity(organizationId, mailId)).thenReturn(mail());
            when(outreachQueryService.get(organizationId, outreachId)).thenReturn(detail);

            // Act
            OutreachDetailResponse result = service.send(organizationId, outreachId, request);

            // Assert
            Mail mail = sentMail();
            assertThat(mail.sender()).isEqualTo("sales@example.com");
            assertThat(mail.recipients()).containsExactly("jan@fabrikam.example");
            assertThat(mail.subject()).isEqualTo("Hello");
            assertThat(mail.content()).isEqualTo("Line &lt;1&gt;<br>Line 2<br>Line 3");
            assertThat(mail.attachments()).isEmpty();
            verify(outreachRepository, never()).markSendFailed(any(), any(), any());
            assertThat(result).isSameAs(detail);
        }

        @Test
        void sharedSender_sendsFromTheGraphObjectIdOfTheSharedMailbox() {
            // Arrange
            Outreach outreach = outreach(OutreachStatus.FAILED);
            when(outreachRepository.findByIdAndOrganizationId(outreachId, organizationId))
                    .thenReturn(Optional.of(outreach));
            when(mailSenderValidator.ensureMailSenderExists(organizationId, senderId))
                    .thenReturn(sender(EmailType.SHARED, "sales@example.com"));
            when(mailSenderValidator.ensureSharedMailboxInTenant(organizationId, "sales@example.com"))
                    .thenReturn(new GraphMailbox("object-id-1", "sales@example.com"));
            when(contactValidator.ensureContactExists(organizationId, relationId, contactId))
                    .thenReturn(Contact.builder().id(contactId).email("jan@fabrikam.example").build());
            claimSucceeds();
            when(mailQueryService.getEntity(organizationId, mailId)).thenReturn(mail());
            when(outreachQueryService.get(organizationId, outreachId)).thenReturn(detail);

            // Act
            service.send(organizationId, outreachId, request);

            // Assert
            assertThat(sentMail().sender()).isEqualTo("object-id-1");
        }

        @Test
        void attachments_areTheMailsAttachmentsInUploadOrderWithTheirOwnNameAndType() {
            // Arrange
            readyToSend();
            claimSucceeds();
            when(attachmentQueryService.getEntityAttachmentContents(StorageEntityType.MAIL, mailId)).thenReturn(List.of(
                    new AttachmentContent("Zeta.pdf", "application/pdf", new byte[] {1, 2}),
                    new AttachmentContent("Alpha.png", "image/png", new byte[] {3})));
            when(mailQueryService.getEntity(organizationId, mailId)).thenReturn(mail());
            when(outreachQueryService.get(organizationId, outreachId)).thenReturn(detail);

            // Act
            service.send(organizationId, outreachId, request);

            // Assert
            List<AttachmentEmail> attachments = sentMail().attachments();
            assertThat(attachments).extracting(AttachmentEmail::filename)
                    .containsExactly("Zeta.pdf", "Alpha.png");
            assertThat(attachments).extracting(AttachmentEmail::contentType)
                    .containsExactly("application/pdf", "image/png");
            assertThat(attachments.get(1).content()).containsExactly(3);
        }

        @Test
        void content_isReadOnlyAfterTheClaim() {
            // Arrange
            readyToSend();
            claimSucceeds();
            when(mailQueryService.getEntity(organizationId, mailId)).thenReturn(mail());
            when(outreachQueryService.get(organizationId, outreachId)).thenReturn(detail);

            // Act
            service.send(organizationId, outreachId, request);

            // Assert
            InOrder order = inOrder(outreachRepository, mailQueryService, msGraphClient);
            order.verify(outreachRepository).claimForSending(outreachId, organizationId, senderId,
                    "jan@fabrikam.example", NOW);
            order.verify(mailQueryService).getEntity(organizationId, mailId);
            order.verify(msGraphClient).sendEmail(any(Mail.class));
        }

        @Test
        void sent_throwsNotEditableAndNeverClaims() {
            // Arrange
            when(outreachRepository.findByIdAndOrganizationId(outreachId, organizationId))
                    .thenReturn(Optional.of(outreach(OutreachStatus.SENT)));

            // Act and assert
            assertThatThrownBy(() -> service.send(organizationId, outreachId, request))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_OUTREACH_NOT_EDITABLE.getCode()));
            verify(outreachRepository, never()).claimForSending(any(), any(), any(), anyString(), any());
            verifyNoInteractions(msGraphClient);
        }

        @Test
        void claimLostToAConcurrentSend_throwsNotEditableAndSendsNothing() {
            // Arrange
            readyToSend();
            when(outreachRepository.claimForSending(outreachId, organizationId, senderId, "jan@fabrikam.example", NOW))
                    .thenReturn(0);

            // Act and assert
            assertThatThrownBy(() -> service.send(organizationId, outreachId, request))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_OUTREACH_NOT_EDITABLE.getCode()));
            verifyNoInteractions(msGraphClient);
            verify(outreachRepository, never()).markSendFailed(any(), any(), any());
        }

        @Test
        void contactWithoutAddress_throwsBeforeTheClaim() {
            // Arrange
            Outreach outreach = outreach(OutreachStatus.DRAFT);
            when(outreachRepository.findByIdAndOrganizationId(outreachId, organizationId))
                    .thenReturn(Optional.of(outreach));
            when(mailSenderValidator.ensureMailSenderExists(organizationId, senderId))
                    .thenReturn(sender(EmailType.PERSONAL, "sales@example.com"));
            when(contactValidator.ensureContactExists(organizationId, relationId, contactId))
                    .thenReturn(Contact.builder().id(contactId).email(" ").build());

            // Act and assert
            assertThatThrownBy(() -> service.send(organizationId, outreachId, request))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_CONTACT_WITHOUT_ADDRESS.getCode()));
            verify(outreachRepository, never()).claimForSending(any(), any(), any(), anyString(), any());
        }

        @Test
        void contactDeleted_throwsContactNotFoundBeforeTheClaim() {
            // Arrange
            Outreach outreach = outreach(OutreachStatus.DRAFT);
            outreach.setContactId(null);
            when(outreachRepository.findByIdAndOrganizationId(outreachId, organizationId))
                    .thenReturn(Optional.of(outreach));
            when(mailSenderValidator.ensureMailSenderExists(organizationId, senderId))
                    .thenReturn(sender(EmailType.PERSONAL, "sales@example.com"));

            // Act and assert
            assertThatThrownBy(() -> service.send(organizationId, outreachId, request))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_CONTACT_FOUND.getCode()));
            verify(outreachRepository, never()).claimForSending(any(), any(), any(), anyString(), any());
        }

        @Test
        void senderOfAnotherOrganization_throwsNotFoundBeforeTheClaim() {
            // Arrange
            when(outreachRepository.findByIdAndOrganizationId(outreachId, organizationId))
                    .thenReturn(Optional.of(outreach(OutreachStatus.DRAFT)));
            NotFoundException notFound = new NotFoundException(ExceptionDomainOtakError.NOT_MAIL_SENDER_FOUND,
                    Map.of());
            when(mailSenderValidator.ensureMailSenderExists(organizationId, senderId)).thenThrow(notFound);

            // Act and assert
            assertThatThrownBy(() -> service.send(organizationId, outreachId, request)).isSameAs(notFound);
            verify(outreachRepository, never()).claimForSending(any(), any(), any(), anyString(), any());
        }

        @Test
        void graphFails_marksFailedAnswersBadGatewayAndCommitsTheMarker() {
            // Arrange
            readyToSend();
            claimSucceeds();
            when(mailQueryService.getEntity(organizationId, mailId)).thenReturn(mail());
            doThrow(new IllegalStateException("graph down")).when(msGraphClient).sendEmail(any(Mail.class));

            // Act and assert
            assertThatThrownBy(() -> service.send(organizationId, outreachId, request))
                    .isInstanceOfSatisfying(BadGatewayException.class, (BadGatewayException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainNotificationError.UPS_MS_GRAPH_COMMUNICATION.getCode()));
            verify(outreachRepository).markSendFailed(outreachId, organizationId, NOW);
            verify(outreachQueryService, never()).get(any(), any());
        }

        @Test
        void graphFailsAndTheFailureMarkerFailsToo_stillAnswersBadGateway() {
            // Arrange
            readyToSend();
            claimSucceeds();
            when(mailQueryService.getEntity(organizationId, mailId)).thenReturn(mail());
            doThrow(new IllegalStateException("graph down")).when(msGraphClient).sendEmail(any(Mail.class));
            when(outreachRepository.markSendFailed(outreachId, organizationId, NOW))
                    .thenThrow(new IllegalStateException("database down"));

            // Act and assert
            assertThatThrownBy(() -> service.send(organizationId, outreachId, request))
                    .isInstanceOf(BadGatewayException.class);
        }

        @Test
        void attachmentDownloadFails_marksFailedRethrowsTheOriginalAndSendsNothing() {
            // Arrange
            readyToSend();
            claimSucceeds();
            when(mailQueryService.getEntity(organizationId, mailId)).thenReturn(mail());
            IntegrationErrorException storageDown = new IntegrationErrorException(
                    ExceptionDomainAzureError.UPS_AZURE_BLOB_DOWNLOAD);
            when(attachmentQueryService.getEntityAttachmentContents(StorageEntityType.MAIL, mailId))
                    .thenThrow(storageDown);

            // Act and assert
            assertThatThrownBy(() -> service.send(organizationId, outreachId, request)).isSameAs(storageDown);
            verify(outreachRepository).markSendFailed(outreachId, organizationId, NOW);
            verifyNoInteractions(msGraphClient);
        }

        @Test
        void attachmentsOverThreeMebibytes_marksFailedWithTooLargeAndSendsNothing() {
            // Arrange
            readyToSend();
            claimSucceeds();
            when(mailQueryService.getEntity(organizationId, mailId)).thenReturn(mail());
            when(attachmentQueryService.getEntityAttachmentContents(StorageEntityType.MAIL, mailId)).thenReturn(List.of(
                    new AttachmentContent("Big.pdf", "application/pdf", new byte[3 * 1024 * 1024 + 1])));

            // Act and assert
            assertThatThrownBy(() -> service.send(organizationId, outreachId, request))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_ATTACHMENTS_TOO_LARGE.getCode()));
            verify(outreachRepository).markSendFailed(outreachId, organizationId, NOW);
            verifyNoInteractions(msGraphClient);
        }
    }

    @Nested
    class Channels {

        private Outreach channelOutreach(OutreachType type, OutreachStatus status) {
            return Outreach.builder().id(outreachId).title("Title").type(type).status(status)
                    .organizationId(organizationId).relationId(relationId).contactId(contactId).build();
        }

        @ParameterizedTest
        @EnumSource(value = OutreachType.class, names = {"WHATSAPP", "CALL"})
        void create_channelWithoutMail_savesADraftWithoutMailAndWritesNoMail(OutreachType type) {
            // Arrange
            when(outreachRepository.saveAndFlush(any(Outreach.class))).thenAnswer(invocation -> {
                Outreach saved = invocation.getArgument(0);
                saved.setId(outreachId);
                return saved;
            });
            when(outreachQueryService.get(organizationId, outreachId)).thenReturn(detail);
            OutreachCreateRequest request = new OutreachCreateRequest(relationId, contactId, " Call Jan ", type,
                    null, null, null);

            // Act
            OutreachDetailResponse result = service.create(organizationId, user, request);

            // Assert
            ArgumentCaptor<Outreach> captor = ArgumentCaptor.forClass(Outreach.class);
            verify(outreachRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(type);
            assertThat(captor.getValue().getMailId()).isNull();
            assertThat(captor.getValue().getStatus()).isEqualTo(OutreachStatus.DRAFT);
            assertThat(captor.getValue().getTitle()).isEqualTo("Call Jan");
            verifyNoInteractions(mailService);
            assertThat(result).isSameAs(detail);
        }

        @Test
        void create_channelWithMail_throwsBeforeCreatingAnything() {
            // Arrange
            OutreachCreateRequest request = new OutreachCreateRequest(relationId, contactId, "Title",
                    OutreachType.WHATSAPP, null, new MailRequest("S", "B", DocumentLanguage.NL), null);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, request))
                    .isInstanceOf(ValidationException.class);
            verifyNoInteractions(mailService);
            verify(outreachRepository, never()).saveAndFlush(any());
        }

        @Test
        void update_channelWithMail_throwsAndWritesNothing() {
            // Arrange
            lockedFinds(channelOutreach(OutreachType.CALL, OutreachStatus.DRAFT));
            OutreachUpdateRequest request = new OutreachUpdateRequest(contactId, "T",
                    new MailRequest("S", "B", DocumentLanguage.NL));

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, outreachId, request))
                    .isInstanceOf(ValidationException.class);
            verifyNoInteractions(mailService);
            verify(outreachRepository, never()).saveAndFlush(any());
        }

        @Test
        void update_channelWithoutMail_writesTheTitleAndNoMail() {
            // Arrange
            Outreach outreach = channelOutreach(OutreachType.CALL, OutreachStatus.DRAFT);
            lockedFinds(outreach);
            when(outreachQueryService.get(organizationId, outreachId)).thenReturn(detail);

            // Act
            service.update(organizationId, outreachId, new OutreachUpdateRequest(contactId, " New ", null));

            // Assert
            assertThat(outreach.getTitle()).isEqualTo("New");
            verifyNoInteractions(mailService);
            verify(outreachRepository).saveAndFlush(outreach);
        }

        @Test
        void delete_channelOutreach_deletesOnlyTheOutreach() {
            // Arrange
            Outreach outreach = channelOutreach(OutreachType.WHATSAPP, OutreachStatus.DRAFT);
            lockedFinds(outreach);

            // Act
            service.delete(organizationId, outreachId);

            // Assert
            verify(outreachRepository).delete(outreach);
            verifyNoInteractions(mailService);
        }

        @Test
        void send_callOutreach_throwsActionNotForTypeBeforeAnyClaim() {
            // Arrange
            when(outreachRepository.findByIdAndOrganizationId(outreachId, organizationId))
                    .thenReturn(Optional.of(channelOutreach(OutreachType.CALL, OutreachStatus.DRAFT)));

            // Act and assert
            assertThatThrownBy(() -> service.send(organizationId, outreachId, new OutreachSendRequest(UUID.randomUUID())))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_ACTION_NOT_FOR_TYPE.getCode()));
            verify(outreachRepository, never()).claimForSending(any(), any(), any(), any(), any());
            verifyNoInteractions(msGraphClient);
        }

        @Test
        void complete_callWithPhone_claimsWithTrimmedPhoneAndNoSender() {
            // Arrange
            lockedFinds(channelOutreach(OutreachType.CALL, OutreachStatus.DRAFT));
            when(contactValidator.ensureContactExists(organizationId, relationId, contactId))
                    .thenReturn(Contact.builder().id(contactId).phone(" +31 6 1234 5678 ").build());
            when(outreachRepository.claimForSending(outreachId, organizationId, null, "+31 6 1234 5678", NOW))
                    .thenReturn(1);
            when(outreachQueryService.get(organizationId, outreachId)).thenReturn(detail);

            // Act
            OutreachDetailResponse result = service.complete(organizationId, outreachId);

            // Assert
            verify(outreachRepository).claimForSending(outreachId, organizationId, null, "+31 6 1234 5678", NOW);
            verifyNoInteractions(msGraphClient);
            assertThat(result).isSameAs(detail);
        }

        @Test
        void complete_mailOutreach_throwsActionNotForTypeAndClaimsNothing() {
            // Arrange
            lockedFinds(outreach(OutreachStatus.DRAFT));

            // Act and assert
            assertThatThrownBy(() -> service.complete(organizationId, outreachId))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_ACTION_NOT_FOR_TYPE.getCode()));
            verify(outreachRepository, never()).claimForSending(any(), any(), any(), any(), any());
        }

        @Test
        void complete_sentOutreach_throwsNotEditableAndClaimsNothing() {
            // Arrange
            lockedFinds(channelOutreach(OutreachType.CALL, OutreachStatus.SENT));

            // Act and assert
            assertThatThrownBy(() -> service.complete(organizationId, outreachId))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_OUTREACH_NOT_EDITABLE.getCode()));
            verify(outreachRepository, never()).claimForSending(any(), any(), any(), any(), any());
        }

        @Test
        void complete_contactWithoutPhone_throwsWithoutPhoneAndClaimsNothing() {
            // Arrange
            lockedFinds(channelOutreach(OutreachType.WHATSAPP, OutreachStatus.DRAFT));
            when(contactValidator.ensureContactExists(organizationId, relationId, contactId))
                    .thenReturn(Contact.builder().id(contactId).phone("  ").build());

            // Act and assert
            assertThatThrownBy(() -> service.complete(organizationId, outreachId))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_CONTACT_WITHOUT_PHONE.getCode()));
            verify(outreachRepository, never()).claimForSending(any(), any(), any(), any(), any());
        }

        @Test
        void complete_claimLost_throwsNotEditable() {
            // Arrange
            lockedFinds(channelOutreach(OutreachType.CALL, OutreachStatus.DRAFT));
            when(contactValidator.ensureContactExists(organizationId, relationId, contactId))
                    .thenReturn(Contact.builder().id(contactId).phone("+31 6 1").build());
            when(outreachRepository.claimForSending(outreachId, organizationId, null, "+31 6 1", NOW)).thenReturn(0);

            // Act and assert
            assertThatThrownBy(() -> service.complete(organizationId, outreachId))
                    .isInstanceOf(ConflictException.class);
        }
    }
}
