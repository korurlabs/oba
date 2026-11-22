package com.korur.oba.shrine.document.service.document.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.service.MailQueryService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.ResourceAccessAction;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.document.dto.request.document.DocumentCreateRequest;
import com.korur.oba.shrine.document.dto.request.document.DocumentUpdateRequest;
import com.korur.oba.shrine.document.entity.document.Document;
import com.korur.oba.shrine.document.entity.template.DocumentTemplate;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.document.enums.common.DocumentType;
import com.korur.oba.shrine.document.enums.document.DocumentEntityType;
import com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError;
import com.korur.oba.shrine.document.helper.common.DocumentFileStorage;
import com.korur.oba.shrine.document.helper.common.StoredDocumentFiles;
import com.korur.oba.shrine.document.mapper.document.DocumentMapperHelper;
import com.korur.oba.shrine.document.repository.document.DocumentRepository;
import com.korur.oba.shrine.document.service.template.DocumentTemplateQueryService;
import com.korur.oba.shrine.document.validator.document.DocumentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.legalentity.exception.ExceptionDomainLegalEntityError;
import com.korur.oba.shrine.legalentity.service.LegalEntityQueryService;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentContent;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID relationId = UUID.randomUUID();
    private final UUID legalEntityId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentMapperHelper documentMapperHelper;

    @Mock
    private DocumentFileStorage documentFileStorage;

    @Mock
    private DocumentTemplateQueryService documentTemplateQueryService;

    @Mock
    private LegalEntityQueryService legalEntityQueryService;

    @Mock
    private ResourceAccessQueryService resourceAccessQueryService;

    @Mock
    private MailQueryService mailQueryService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private LoggingService loggingService;

    @Mock
    private MultipartFile html;

    @Mock
    private MultipartFile pdf;

    private DocumentServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        // The real validator runs over the mocked repository, so the error codes are the production ones.
        service = new DocumentServiceImpl(documentRepository, new DocumentValidator(documentRepository, loggingService),
                documentMapperHelper, documentFileStorage, documentTemplateQueryService, legalEntityQueryService,
                resourceAccessQueryService, mailQueryService, attachmentService, loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    private void savesWhatItIsGiven() {
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Document stored(String htmlUrl, String pdfUrl) {
        Document document = Document.builder()
                .id(documentId)
                .organizationId(organizationId)
                .entityType(DocumentEntityType.RELATION)
                .entityId(relationId)
                .legalEntityId(legalEntityId)
                .title("Proposal Fabrikam")
                .type(DocumentType.PROPOSAL)
                .language(DocumentLanguage.EN)
                .htmlBlobUrl(htmlUrl)
                .pdfBlobUrl(pdfUrl)
                .createdById(UUID.randomUUID())
                .updatedById(UUID.randomUUID())
                .build();
        when(documentRepository.findByIdAndOrganizationId(documentId, organizationId))
                .thenReturn(Optional.of(document));
        return document;
    }

    private DocumentCreateRequest createRequest(UUID templateId) {
        return new DocumentCreateRequest(DocumentEntityType.RELATION, relationId, legalEntityId, "  Proposal  ",
                DocumentType.PROPOSAL, DocumentLanguage.EN, templateId);
    }

    @Nested
    class Create {

        @Test
        void readyTemplate_copiesItsFilesToNewBlobsAndSetsBothAuthors() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            DocumentTemplate template = DocumentTemplate.builder()
                    .id(templateId)
                    .htmlBlobUrl("https://blob/template.html")
                    .pdfBlobUrl("https://blob/template.pdf")
                    .build();
            when(documentTemplateQueryService.getEntity(organizationId, templateId)).thenReturn(template);
            when(documentFileStorage.copy("https://blob/template.html", "https://blob/template.pdf"))
                    .thenReturn(new StoredDocumentFiles("https://blob/copy.html", "https://blob/copy.pdf"));
            savesWhatItIsGiven();

            // Act
            service.create(organizationId, user, createRequest(templateId));

            // Assert
            ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
            verify(documentRepository).saveAndFlush(captor.capture());
            Document saved = captor.getValue();
            assertThat(saved.getHtmlBlobUrl()).isEqualTo("https://blob/copy.html");
            assertThat(saved.getPdfBlobUrl()).isEqualTo("https://blob/copy.pdf");
            assertThat(saved.getTemplateId()).isEqualTo(templateId);
            assertThat(saved.getTitle()).isEqualTo("Proposal");
            assertThat(saved.getOrganizationId()).isEqualTo(organizationId);
            assertThat(saved.getCreatedById()).isEqualTo(user.getId());
            assertThat(saved.getUpdatedById()).isEqualTo(user.getId());
            verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId, ResourceAccessType.RELATION,
                    relationId, ResourceAccessAction.WRITE);
        }

        @Test
        void templateWithoutFiles_createsADocumentWithoutFiles() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            when(documentTemplateQueryService.getEntity(organizationId, templateId))
                    .thenReturn(DocumentTemplate.builder().id(templateId).build());
            savesWhatItIsGiven();

            // Act
            service.create(organizationId, user, createRequest(templateId));

            // Assert
            ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
            verify(documentRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getHtmlBlobUrl()).isNull();
            assertThat(captor.getValue().getPdfBlobUrl()).isNull();
            verifyNoInteractions(documentFileStorage);
        }

        @Test
        void legalEntityOfAnotherOrganization_throwsNotFoundBeforeAnyBlobOrRow() {
            // Arrange
            when(legalEntityQueryService.getEntity(organizationId, legalEntityId))
                    .thenThrow(new NotFoundException(ExceptionDomainLegalEntityError.NOT_LEGAL_ENTITY_FOUND));

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, createRequest(UUID.randomUUID())))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainLegalEntityError.NOT_LEGAL_ENTITY_FOUND.getCode());
            verifyNoInteractions(documentFileStorage);
            verify(documentRepository, never()).saveAndFlush(any());
        }

        @Test
        void callerWithoutWriteOnTheRelation_isRefusedBeforeAnyLookup() {
            // Arrange
            doThrow(new ForbiddenException()).when(resourceAccessQueryService).verifyResourceAccess(user,
                    organizationId, ResourceAccessType.RELATION, relationId, ResourceAccessAction.WRITE);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, createRequest(null)))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(legalEntityQueryService, documentFileStorage);
            verify(documentRepository, never()).saveAndFlush(any());
        }

        @Test
        void failedSave_deletesTheCopiedBlobsAndRethrows() {
            // Arrange
            UUID templateId = UUID.randomUUID();
            when(documentTemplateQueryService.getEntity(organizationId, templateId)).thenReturn(DocumentTemplate
                    .builder().id(templateId).htmlBlobUrl("https://blob/t.html").pdfBlobUrl("https://blob/t.pdf").build());
            when(documentFileStorage.copy(anyString(), anyString()))
                    .thenReturn(new StoredDocumentFiles("https://blob/copy.html", "https://blob/copy.pdf"));
            when(documentRepository.saveAndFlush(any(Document.class)))
                    .thenThrow(new DataIntegrityViolationException("boom"));

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, createRequest(templateId)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            verify(documentFileStorage).deleteQuietly("https://blob/copy.html", "https://blob/copy.pdf");
        }
    }

    @Nested
    class Update {

        @Test
        void request_replacesFieldsAndSetsTheLastEditor() {
            // Arrange
            Document document = stored("https://blob/a.html", "https://blob/a.pdf");
            UUID otherLegalEntityId = UUID.randomUUID();
            savesWhatItIsGiven();
            DocumentUpdateRequest request = new DocumentUpdateRequest(" NDA ", DocumentType.NDA, DocumentLanguage.NL,
                    otherLegalEntityId);

            // Act
            service.update(organizationId, user, documentId, request);

            // Assert
            assertThat(document.getTitle()).isEqualTo("NDA");
            assertThat(document.getType()).isEqualTo(DocumentType.NDA);
            assertThat(document.getLanguage()).isEqualTo(DocumentLanguage.NL);
            assertThat(document.getLegalEntityId()).isEqualTo(otherLegalEntityId);
            assertThat(document.getUpdatedById()).isEqualTo(user.getId());
            assertThat(document.getHtmlBlobUrl()).isEqualTo("https://blob/a.html");
            verify(legalEntityQueryService).getEntity(organizationId, otherLegalEntityId);
        }

        @Test
        void documentOfAnotherOrganization_throwsNotFound() {
            // Arrange
            when(documentRepository.findByIdAndOrganizationId(documentId, organizationId)).thenReturn(Optional.empty());
            DocumentUpdateRequest request = new DocumentUpdateRequest("NDA", DocumentType.NDA, DocumentLanguage.NL,
                    legalEntityId);

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, user, documentId, request))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainDocumentError.NOT_DOCUMENT_FOUND.getCode());
            verifyNoInteractions(resourceAccessQueryService);
        }
    }

    @Nested
    class ReplaceFiles {

        @Test
        void newFiles_areSavedThenTheOldBlobsRemovedAndTheLastEditorSet() {
            // Arrange
            Document document = stored("https://blob/old.html", "https://blob/old.pdf");
            when(documentFileStorage.store(html, pdf))
                    .thenReturn(new StoredDocumentFiles("https://blob/new.html", "https://blob/new.pdf"));
            savesWhatItIsGiven();

            // Act
            service.replaceFiles(organizationId, user, documentId, html, pdf);

            // Assert
            assertThat(document.getHtmlBlobUrl()).isEqualTo("https://blob/new.html");
            assertThat(document.getPdfBlobUrl()).isEqualTo("https://blob/new.pdf");
            assertThat(document.getUpdatedById()).isEqualTo(user.getId());
            InOrder order = inOrder(resourceAccessQueryService, documentFileStorage, documentRepository);
            order.verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId,
                    ResourceAccessType.RELATION, relationId, ResourceAccessAction.WRITE);
            order.verify(documentFileStorage).store(html, pdf);
            order.verify(documentRepository).saveAndFlush(document);
            order.verify(documentFileStorage).deleteQuietly("https://blob/old.html", "https://blob/old.pdf");
        }

        @Test
        void callerWithoutWrite_storesNoBlob() {
            // Arrange
            stored(null, null);
            doThrow(new ForbiddenException()).when(resourceAccessQueryService).verifyResourceAccess(user,
                    organizationId, ResourceAccessType.RELATION, relationId, ResourceAccessAction.WRITE);

            // Act and assert
            assertThatThrownBy(() -> service.replaceFiles(organizationId, user, documentId, html, pdf))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(documentFileStorage);
        }
    }

    @Nested
    class Delete {

        @Test
        void document_isDeletedAndFlushedBeforeItsBlobs() {
            // Arrange
            Document document = stored("https://blob/a.html", "https://blob/a.pdf");

            // Act
            service.delete(organizationId, user, documentId);

            // Assert
            InOrder order = inOrder(resourceAccessQueryService, documentRepository, documentFileStorage);
            order.verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId,
                    ResourceAccessType.RELATION, relationId, ResourceAccessAction.DELETE);
            order.verify(documentRepository).delete(document);
            order.verify(documentRepository).flush();
            order.verify(documentFileStorage).deleteQuietly("https://blob/a.html", "https://blob/a.pdf");
        }
    }

    @Nested
    class AttachTo {

        private final UUID mailId = UUID.randomUUID();

        @Test
        void mailOfTheSameRelation_storesTheCurrentPdfAsASnapshotWithTheDocumentId() {
            // Arrange
            stored("https://blob/a.html", "https://blob/a.pdf");
            when(mailQueryService.getEntity(organizationId, mailId))
                    .thenReturn(Mail.builder().id(mailId).relationId(relationId).build());
            byte[] bytes = "%PDF-1.7".getBytes();
            when(documentFileStorage.readPdf("https://blob/a.pdf")).thenReturn(bytes);
            AttachmentResponse stored = new AttachmentResponse(UUID.randomUUID(), "signed", "Proposal Fabrikam.pdf",
                    "application/pdf", 8L, StorageEntityType.MAIL, mailId, Instant.now(), null, null, documentId);
            when(attachmentService.storeDocumentSnapshot(eq(user), eq(organizationId), eq(StorageEntityType.MAIL),
                    eq(mailId), any(AttachmentContent.class), eq(documentId))).thenReturn(stored);

            // Act
            AttachmentResponse response = service.attachTo(user, organizationId, documentId, StorageEntityType.MAIL,
                    mailId);

            // Assert
            assertThat(response).isSameAs(stored);
            ArgumentCaptor<AttachmentContent> captor = ArgumentCaptor.forClass(AttachmentContent.class);
            verify(attachmentService).storeDocumentSnapshot(eq(user), eq(organizationId), eq(StorageEntityType.MAIL),
                    eq(mailId), captor.capture(), eq(documentId));
            assertThat(captor.getValue().fileName()).isEqualTo("Proposal Fabrikam.pdf");
            assertThat(captor.getValue().contentType()).isEqualTo("application/pdf");
            assertThat(captor.getValue().content()).isSameAs(bytes);
            verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId, ResourceAccessType.RELATION,
                    relationId, ResourceAccessAction.READ);
        }

        @Test
        void parentOtherThanAMail_throwsValidationBeforeAnyLookup() {
            // Act and assert
            assertThatThrownBy(() -> service.attachTo(user, organizationId, documentId, StorageEntityType.RELATION,
                    relationId))
                    .isInstanceOf(ValidationException.class);
            verifyNoInteractions(documentRepository, attachmentService);
        }

        @Test
        void documentWithoutPdf_throwsValidationWithoutPdf() {
            // Arrange
            stored(null, null);

            // Act and assert
            assertThatThrownBy(() -> service.attachTo(user, organizationId, documentId, StorageEntityType.MAIL, mailId))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainDocumentError.VAL_DOCUMENT_WITHOUT_PDF.getCode());
            verifyNoInteractions(mailQueryService, attachmentService);
        }

        @Test
        void mailOfAnotherRelation_throwsValidationOtherRelation() {
            // Arrange
            stored("https://blob/a.html", "https://blob/a.pdf");
            when(mailQueryService.getEntity(organizationId, mailId))
                    .thenReturn(Mail.builder().id(mailId).relationId(UUID.randomUUID()).build());

            // Act and assert
            assertThatThrownBy(() -> service.attachTo(user, organizationId, documentId, StorageEntityType.MAIL, mailId))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainDocumentError.VAL_DOCUMENT_OTHER_RELATION.getCode());
            verifyNoInteractions(attachmentService);
            verify(documentFileStorage, never()).readPdf(any());
        }

        @Test
        void mailOfAnotherOrganization_throwsNotFound() {
            // Arrange
            stored("https://blob/a.html", "https://blob/a.pdf");
            when(mailQueryService.getEntity(organizationId, mailId))
                    .thenThrow(new NotFoundException(ExceptionDomainOtakError.NOT_MAIL_FOUND, Map.of()));

            // Act and assert
            assertThatThrownBy(() -> service.attachTo(user, organizationId, documentId, StorageEntityType.MAIL, mailId))
                    .isInstanceOf(NotFoundException.class);
            verifyNoInteractions(attachmentService);
        }
    }

    @Nested
    class DeleteDocumentsByEntity {

        @Test
        void documentsOfTheOwner_areDeletedThenTheirBlobs() {
            // Arrange
            Document first = Document.builder().id(UUID.randomUUID()).htmlBlobUrl("h1").pdfBlobUrl("p1").build();
            Document second = Document.builder().id(UUID.randomUUID()).build();
            when(documentRepository.findByEntityTypeAndEntityId(DocumentEntityType.RELATION, relationId))
                    .thenReturn(List.of(first, second));

            // Act
            service.deleteDocumentsByEntity(DocumentEntityType.RELATION, relationId);

            // Assert
            InOrder order = inOrder(documentRepository, documentFileStorage);
            order.verify(documentRepository).deleteAllInBatch(List.of(first, second));
            order.verify(documentFileStorage).deleteQuietly("h1", "p1");
            order.verify(documentFileStorage).deleteQuietly(null, null);
        }

        @Test
        void ownerWithoutDocuments_deletesNothing() {
            // Arrange
            when(documentRepository.findByEntityTypeAndEntityId(DocumentEntityType.RELATION, relationId))
                    .thenReturn(List.of());

            // Act
            service.deleteDocumentsByEntity(DocumentEntityType.RELATION, relationId);

            // Assert
            verify(documentRepository, never()).deleteAllInBatch(any());
            verifyNoInteractions(documentFileStorage);
        }
    }
}
