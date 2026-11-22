package com.korur.oba.shrine.document.service.template.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.connect.azure.service.BlobUrlRewriterMapper;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.document.dto.request.template.DocumentTemplateCreateRequest;
import com.korur.oba.shrine.document.dto.request.template.DocumentTemplateUpdateRequest;
import com.korur.oba.shrine.document.dto.response.template.DocumentTemplateDetailResponse;
import com.korur.oba.shrine.document.entity.template.DocumentTemplate;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.document.enums.common.DocumentType;
import com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError;
import com.korur.oba.shrine.document.helper.common.DocumentFileStorage;
import com.korur.oba.shrine.document.helper.common.StoredDocumentFiles;
import com.korur.oba.shrine.document.mapper.template.DocumentTemplateMapperImpl;
import com.korur.oba.shrine.document.repository.template.DocumentTemplateRepository;
import com.korur.oba.shrine.document.validator.template.DocumentTemplateValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.legalentity.entity.LegalEntity;
import com.korur.oba.shrine.legalentity.exception.ExceptionDomainLegalEntityError;
import com.korur.oba.shrine.legalentity.service.LegalEntityQueryService;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentContent;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

@ExtendWith(MockitoExtension.class)
class DocumentTemplateServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private DocumentTemplateRepository documentTemplateRepository;

    @Mock
    private DocumentFileStorage documentFileStorage;

    @Mock
    private BlobUrlRewriterMapper blobUrlRewriterMapper;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private LegalEntityQueryService legalEntityQueryService;

    @Mock
    private LoggingService loggingService;

    @Mock
    private MultipartFile html;

    @Mock
    private MultipartFile pdf;

    private DocumentTemplateServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        // The real validator and mapper run over the mocked repository and file storage, so the
        // error codes and the mapped fields are the production ones.
        service = new DocumentTemplateServiceImpl(documentTemplateRepository,
                new DocumentTemplateMapperImpl(blobUrlRewriterMapper),
                new DocumentTemplateValidator(documentTemplateRepository, loggingService),
                documentFileStorage, attachmentService, legalEntityQueryService, loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
        lenient().when(blobUrlRewriterMapper.rewriteBlobUrl(any()))
                .thenAnswer(invocation -> invocation.getArgument(0) == null
                        ? null : "signed:" + invocation.getArgument(0));
    }

    private void savesWhatItIsGiven() {
        when(documentTemplateRepository.save(any(DocumentTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private DocumentTemplate stored(UUID id, DocumentType type) {
        return DocumentTemplate.builder()
                .id(id)
                .name("Stored")
                .type(type)
                .language(DocumentLanguage.NL)
                .htmlBlobUrl("https://blob/old.html")
                .pdfBlobUrl("https://blob/old.pdf")
                .organizationId(organizationId)
                .build();
    }

    @Nested
    class Create {

        @Test
        void request_savesTrimmedNameOrganizationTypeAndLanguage() {
            // Arrange
            savesWhatItIsGiven();
            DocumentTemplateCreateRequest request = new DocumentTemplateCreateRequest(
                    DocumentType.PROPOSAL, "  First contact  ", DocumentLanguage.NL, null);

            // Act
            DocumentTemplateDetailResponse response = service.create(organizationId, user, request);

            // Assert
            ArgumentCaptor<DocumentTemplate> captor = ArgumentCaptor.forClass(DocumentTemplate.class);
            verify(documentTemplateRepository).save(captor.capture());
            DocumentTemplate saved = captor.getValue();
            assertThat(saved.getOrganizationId()).isEqualTo(organizationId);
            assertThat(saved.getName()).isEqualTo("First contact");
            assertThat(saved.getType()).isEqualTo(DocumentType.PROPOSAL);
            assertThat(saved.getLanguage()).isEqualTo(DocumentLanguage.NL);
            assertThat(saved.getHtmlBlobUrl()).isNull();
            assertThat(saved.getLegalEntityId()).isNull();
            assertThat(saved.getCreatedById()).isEqualTo(user.getId());
            assertThat(saved.getUpdatedById()).isEqualTo(user.getId());
            assertThat(response.legalEntityShortName()).isNull();
            verifyNoInteractions(legalEntityQueryService);
        }

        @Test
        void legalEntityOfOrganization_isSavedAndItsShortNameReturned() {
            // Arrange
            savesWhatItIsGiven();
            UUID legalEntityId = UUID.randomUUID();
            when(legalEntityQueryService.getEntity(organizationId, legalEntityId))
                    .thenReturn(LegalEntity.builder().id(legalEntityId).shortName("Korur B.V.").build());
            DocumentTemplateCreateRequest request = new DocumentTemplateCreateRequest(
                    DocumentType.CONTRACT, "Service agreement", DocumentLanguage.EN, legalEntityId);

            // Act
            DocumentTemplateDetailResponse response = service.create(organizationId, user, request);

            // Assert
            ArgumentCaptor<DocumentTemplate> captor = ArgumentCaptor.forClass(DocumentTemplate.class);
            verify(documentTemplateRepository).save(captor.capture());
            assertThat(captor.getValue().getLegalEntityId()).isEqualTo(legalEntityId);
            assertThat(captor.getValue().getType()).isEqualTo(DocumentType.CONTRACT);
            assertThat(response.legalEntityId()).isEqualTo(legalEntityId);
            assertThat(response.legalEntityShortName()).isEqualTo("Korur B.V.");
        }

        @Test
        void legalEntityOfAnotherOrganization_throwsNotFoundAndStoresNothing() {
            // Arrange
            UUID legalEntityId = UUID.randomUUID();
            when(legalEntityQueryService.getEntity(organizationId, legalEntityId)).thenThrow(new NotFoundException(
                    ExceptionDomainLegalEntityError.NOT_LEGAL_ENTITY_FOUND, Map.of("legalEntityId", legalEntityId)));
            DocumentTemplateCreateRequest request = new DocumentTemplateCreateRequest(
                    DocumentType.NDA, "NDA", DocumentLanguage.EN, legalEntityId);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, request))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("LGE_NOT_001"));
            verify(documentTemplateRepository, never()).save(any());
        }

        @Test
        void newTemplate_hasNoFilesAndIsNotReady() {
            // Arrange
            savesWhatItIsGiven();
            DocumentTemplateCreateRequest request = new DocumentTemplateCreateRequest(
                    DocumentType.NDA, "NDA", DocumentLanguage.EN, null);

            // Act
            DocumentTemplateDetailResponse response = service.create(organizationId, user, request);

            // Assert
            assertThat(response.ready()).isFalse();
            assertThat(response.htmlUrl()).isNull();
            assertThat(response.pdfUrl()).isNull();
        }

        @Test
        void nameTakenCaseIgnored_throwsConflictAndStoresNothing() {
            // Arrange
            when(documentTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguage(
                    organizationId, "First contact", DocumentLanguage.NL)).thenReturn(true);
            DocumentTemplateCreateRequest request = new DocumentTemplateCreateRequest(
                    DocumentType.PROPOSAL, " First contact ", DocumentLanguage.NL, null);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, request))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainDocumentError.STA_DOCUMENT_TEMPLATE_NAME_TAKEN.getCode()));
            verify(documentTemplateRepository, never()).save(any());
        }
    }

    @Nested
    class Update {

        @Test
        void template_replacesNameTypeAndLanguage() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            DocumentTemplate template = stored(templateId, DocumentType.PROPOSAL);
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.of(template));
            when(documentTemplateRepository.saveAndFlush(template)).thenReturn(template);

            // Act
            DocumentTemplateDetailResponse response = service.update(organizationId, user, templateId,
                    new DocumentTemplateUpdateRequest(DocumentType.NDA, " Renamed ", DocumentLanguage.EN, null));

            // Assert
            assertThat(template.getName()).isEqualTo("Renamed");
            assertThat(template.getType()).isEqualTo(DocumentType.NDA);
            assertThat(template.getLanguage()).isEqualTo(DocumentLanguage.EN);
            assertThat(response.type()).isEqualTo(DocumentType.NDA);
            assertThat(template.getUpdatedById()).isEqualTo(user.getId());
        }

        @Test
        void nullLegalEntity_clearsTheStoredOne() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            DocumentTemplate template = stored(templateId, DocumentType.CONTRACT);
            template.setLegalEntityId(UUID.randomUUID());
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.of(template));
            when(documentTemplateRepository.saveAndFlush(template)).thenReturn(template);

            // Act
            DocumentTemplateDetailResponse response = service.update(organizationId, user, templateId,
                    new DocumentTemplateUpdateRequest(DocumentType.CONTRACT, "Stored", DocumentLanguage.NL, null));

            // Assert
            assertThat(template.getLegalEntityId()).isNull();
            assertThat(response.legalEntityShortName()).isNull();
            verifyNoInteractions(legalEntityQueryService);
        }

        @Test
        void legalEntityOfAnotherOrganization_throwsNotFoundAndSavesNothing() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            UUID legalEntityId = UUID.randomUUID();
            DocumentTemplate template = stored(templateId, DocumentType.NDA);
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.of(template));
            when(legalEntityQueryService.getEntity(organizationId, legalEntityId)).thenThrow(new NotFoundException(
                    ExceptionDomainLegalEntityError.NOT_LEGAL_ENTITY_FOUND, Map.of("legalEntityId", legalEntityId)));

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, user, templateId,
                    new DocumentTemplateUpdateRequest(DocumentType.NDA, "Stored", DocumentLanguage.NL, legalEntityId)))
                    .isInstanceOf(NotFoundException.class);
            assertThat(template.getLegalEntityId()).isNull();
            verify(documentTemplateRepository, never()).saveAndFlush(any());
        }

        @Test
        void template_keepsTheFiles() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            DocumentTemplate template = stored(templateId, DocumentType.NDA);
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.of(template));
            when(documentTemplateRepository.saveAndFlush(template)).thenReturn(template);

            // Act
            service.update(organizationId, user, templateId,
                    new DocumentTemplateUpdateRequest(DocumentType.NDA, "Renamed", DocumentLanguage.NL, null));

            // Assert
            assertThat(template.getHtmlBlobUrl()).isEqualTo("https://blob/old.html");
            assertThat(template.getPdfBlobUrl()).isEqualTo("https://blob/old.pdf");
        }

        @Test
        void nameTakenByAnotherTemplate_throwsConflict() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.of(stored(templateId, DocumentType.PROPOSAL)));
            when(documentTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguageAndIdNot(
                    organizationId, "Taken", DocumentLanguage.NL, templateId)).thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, user, templateId,
                    new DocumentTemplateUpdateRequest(DocumentType.PROPOSAL, "Taken", DocumentLanguage.NL, null)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void templateOfAnotherOrganization_throwsNotFound() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, user, templateId,
                    new DocumentTemplateUpdateRequest(DocumentType.PROPOSAL, "N", DocumentLanguage.NL, null)))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class ReplaceFiles {

        @Test
        void fileTemplate_savesTheNewUrlsAndDeletesTheOldBlobsAfterTheSave() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            DocumentTemplate template = stored(templateId, DocumentType.NDA);
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.of(template));
            when(documentFileStorage.store(html, pdf))
                    .thenReturn(new StoredDocumentFiles("https://blob/new.html", "https://blob/new.pdf"));
            when(documentTemplateRepository.saveAndFlush(template)).thenReturn(template);

            // Act
            DocumentTemplateDetailResponse response = service.replaceFiles(organizationId, user, templateId, html, pdf);

            // Assert
            assertThat(response.htmlUrl()).isEqualTo("signed:https://blob/new.html");
            assertThat(response.pdfUrl()).isEqualTo("signed:https://blob/new.pdf");
            assertThat(response.ready()).isTrue();
            assertThat(template.getUpdatedById()).isEqualTo(user.getId());
            InOrder order = inOrder(documentTemplateRepository, documentFileStorage);
            order.verify(documentTemplateRepository).saveAndFlush(template);
            order.verify(documentFileStorage).deleteQuietly("https://blob/old.html", "https://blob/old.pdf");
        }

        @Test
        void saveFails_deletesTheNewBlobsKeepsTheOldOnesAndRethrows() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            DocumentTemplate template = stored(templateId, DocumentType.NDA);
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.of(template));
            when(documentFileStorage.store(html, pdf))
                    .thenReturn(new StoredDocumentFiles("https://blob/new.html", "https://blob/new.pdf"));
            IllegalStateException failure = new IllegalStateException("database down");
            when(documentTemplateRepository.saveAndFlush(template)).thenThrow(failure);

            // Act and assert
            assertThatThrownBy(() -> service.replaceFiles(organizationId, user, templateId, html, pdf)).isSameAs(failure);
            verify(documentFileStorage).deleteQuietly("https://blob/new.html", "https://blob/new.pdf");
            verify(documentFileStorage, never()).deleteQuietly("https://blob/old.html", "https://blob/old.pdf");
        }

        @Test
        void storageRefusesTheFiles_savesNothingAndDeletesNothing() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.of(stored(templateId, DocumentType.NDA)));
            ValidationException refusal = new ValidationException(
                    ExceptionDomainDocumentError.VAL_FILE_NOT_HTML_OR_PDF, Map.of("pdf", "not a pdf"));
            when(documentFileStorage.store(html, pdf)).thenThrow(refusal);

            // Act and assert
            assertThatThrownBy(() -> service.replaceFiles(organizationId, user, templateId, html, pdf)).isSameAs(refusal);
            verify(documentTemplateRepository, never()).saveAndFlush(any());
            verify(documentFileStorage, never()).deleteQuietly(any());
        }
    }

    @Nested
    class Delete {

        @Test
        void fileTemplate_deletesTheRowThenItsBlobs() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            DocumentTemplate template = stored(templateId, DocumentType.NDA);
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.of(template));

            // Act
            service.delete(organizationId, templateId);

            // Assert
            InOrder order = inOrder(documentTemplateRepository, documentFileStorage);
            order.verify(documentTemplateRepository).delete(template);
            order.verify(documentTemplateRepository).flush();
            order.verify(documentFileStorage).deleteQuietly("https://blob/old.html", "https://blob/old.pdf");
        }

        @Test
        void templateOfAnotherOrganization_throwsNotFoundAndDeletesNothing() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, templateId))
                    .isInstanceOf(NotFoundException.class);
            verify(documentTemplateRepository, never()).delete(any(DocumentTemplate.class));
            verify(documentFileStorage, never()).deleteQuietly(any());
        }
    }

    @Nested
    class CopyToAttachment {

        private final UUID templateId = UUID.randomUUID();
        private final UUID mailId = UUID.randomUUID();

        private AttachmentResponse response() {
            return new AttachmentResponse(UUID.randomUUID(), "url", "x", "application/pdf", 3L,
                    StorageEntityType.MAIL, mailId, null, null, null);
        }

        @Test
        void templateWithPdf_storesTheBytesNamedAfterTheTemplateAsPdf() {
            DocumentTemplate template = stored(templateId, DocumentType.NDA);
            template.setName("Voorstel");
            byte[] bytes = {1, 2, 3};
            AttachmentResponse expected = response();
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.of(template));
            when(documentFileStorage.readPdf("https://blob/old.pdf")).thenReturn(bytes);
            when(attachmentService.storeAttachmentContent(eq(user), eq(organizationId), eq(StorageEntityType.MAIL),
                    eq(mailId), any(AttachmentContent.class))).thenReturn(expected);

            AttachmentResponse result = service.copyToAttachment(user, organizationId, templateId,
                    StorageEntityType.MAIL, mailId);

            assertThat(result).isSameAs(expected);
            ArgumentCaptor<AttachmentContent> captor = ArgumentCaptor.forClass(AttachmentContent.class);
            verify(attachmentService).storeAttachmentContent(eq(user), eq(organizationId),
                    eq(StorageEntityType.MAIL), eq(mailId), captor.capture());
            assertThat(captor.getValue().fileName()).isEqualTo("Voorstel.pdf");
            assertThat(captor.getValue().contentType()).isEqualTo("application/pdf");
            assertThat(captor.getValue().content()).isSameAs(bytes);
        }

        @Test
        void relationTarget_isPassedThrough() {
            DocumentTemplate template = stored(templateId, DocumentType.NDA);
            UUID relationId = UUID.randomUUID();
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.of(template));
            when(documentFileStorage.readPdf(any())).thenReturn(new byte[]{1});
            when(attachmentService.storeAttachmentContent(any(), any(), any(), any(), any())).thenReturn(response());

            service.copyToAttachment(user, organizationId, templateId, StorageEntityType.RELATION, relationId);

            verify(attachmentService).storeAttachmentContent(eq(user), eq(organizationId),
                    eq(StorageEntityType.RELATION), eq(relationId), any(AttachmentContent.class));
        }

        @Test
        void templateWithoutPdf_throwsDocVal002AndReadsAndStoresNothing() {
            DocumentTemplate template = stored(templateId, DocumentType.NDA);
            template.setPdfBlobUrl(null);
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.of(template));

            assertThatThrownBy(() -> service.copyToAttachment(user, organizationId, templateId,
                    StorageEntityType.MAIL, mailId))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("DOC_VAL_002"));
            verify(documentFileStorage, never()).readPdf(any());
            verify(attachmentService, never()).storeAttachmentContent(any(), any(), any(), any(), any());
        }

        @Test
        void templateOfAnotherOrganization_throwsNotFoundAndStoresNothing() {
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.copyToAttachment(user, organizationId, templateId,
                    StorageEntityType.MAIL, mailId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("DOC_NOT_001"));
            verify(documentFileStorage, never()).readPdf(any());
            verify(attachmentService, never()).storeAttachmentContent(any(), any(), any(), any(), any());
        }

        @Test
        void entityTypeNotMailOrRelation_throwsValidationBeforeReadingTheTemplate() {
            assertThatThrownBy(() -> service.copyToAttachment(user, organizationId, templateId,
                    StorageEntityType.TICKET, mailId))
                    .isInstanceOf(ValidationException.class);

            verify(documentTemplateRepository, never()).findByIdAndOrganizationId(any(), any());
            verify(attachmentService, never()).storeAttachmentContent(any(), any(), any(), any(), any());
        }

        @Test
        void storageRefuses_errorPropagatesAndNothingElseHappens() {
            DocumentTemplate template = stored(templateId, DocumentType.NDA);
            when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                    .thenReturn(Optional.of(template));
            when(documentFileStorage.readPdf(any())).thenReturn(new byte[]{1});
            when(attachmentService.storeAttachmentContent(any(), any(), any(), any(), any()))
                    .thenThrow(new ConflictException(ExceptionDomainOtakError.STA_OUTREACH_NOT_EDITABLE));

            assertThatThrownBy(() -> service.copyToAttachment(user, organizationId, templateId,
                    StorageEntityType.MAIL, mailId))
                    .isInstanceOf(ConflictException.class);
        }
    }
}
