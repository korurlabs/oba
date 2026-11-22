package com.korur.oba.shrine.document.service.document.impl;

// 1. Framework-level
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.shrine.document.constant.service.document.DocumentServiceLogConstant.*;

import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.service.MailQueryService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.ResourceAccessAction;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.document.constant.common.DocumentFileConstant;
import com.korur.oba.shrine.document.dto.request.document.DocumentCreateRequest;
import com.korur.oba.shrine.document.dto.request.document.DocumentUpdateRequest;
import com.korur.oba.shrine.document.dto.response.document.DocumentDetailResponse;
import com.korur.oba.shrine.document.entity.document.Document;
import com.korur.oba.shrine.document.entity.template.DocumentTemplate;
import com.korur.oba.shrine.document.enums.document.DocumentEntityType;
import com.korur.oba.shrine.document.helper.common.DocumentFileStorage;
import com.korur.oba.shrine.document.helper.common.StoredDocumentFiles;
import com.korur.oba.shrine.document.mapper.document.DocumentMapperHelper;
import com.korur.oba.shrine.document.repository.document.DocumentRepository;
import com.korur.oba.shrine.document.service.document.DocumentService;
import com.korur.oba.shrine.document.service.template.DocumentTemplateQueryService;
import com.korur.oba.shrine.document.validator.document.DocumentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.legalentity.service.LegalEntityQueryService;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentContent;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

/**
 * Implements the writes to documents. The contract is on {@link DocumentService}; this class
 * documents how each write is carried out.
 *
 * Every refusal (owner access, legal entity, template) happens before a blob is uploaded, so a
 * refused request changes neither the database nor Blob Storage. Blob Storage takes no part
 * in the transaction, so files follow the fixed order of the template service: upload new
 * blobs, write and flush the row, delete the new blobs again if that write fails, and delete
 * the old blobs only once no row points at them.
 *
 * @see com.korur.oba.shrine.document.service.document.DocumentQueryService
 */
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DocumentServiceImpl.class);

    private final DocumentRepository documentRepository;
    private final DocumentValidator documentValidator;
    private final DocumentMapperHelper documentMapperHelper;
    private final DocumentFileStorage documentFileStorage;
    private final DocumentTemplateQueryService documentTemplateQueryService;
    private final LegalEntityQueryService legalEntityQueryService;
    private final ResourceAccessQueryService resourceAccessQueryService;
    private final MailQueryService mailQueryService;
    private final AttachmentService attachmentService;
    private final LoggingService loggingService;

    /**
     * {@inheritDoc}
     *
     * Steps: WRITE on the owner, the legal entity, the template (all before any blob), copy the
     * template's files when it has both, save and flush the row, and remove the copies again
     * if that write fails.
     */
    @Override
    @Transactional
    public DocumentDetailResponse create(UUID organizationId, User user, DocumentCreateRequest request) {
        verifyOwner(user, organizationId, request.entityType(), request.entityId(), ResourceAccessAction.WRITE);
        legalEntityQueryService.getEntity(organizationId, request.legalEntityId());
        DocumentTemplate template = request.templateId() == null
                ? null
                : documentTemplateQueryService.getEntity(organizationId, request.templateId());

        // A template without files gives a document without files; it receives them by upload.
        StoredDocumentFiles copied = template != null && template.isReady()
                ? documentFileStorage.copy(template.getHtmlBlobUrl(), template.getPdfBlobUrl())
                : null;

        Document document = Document.builder()
                .organizationId(organizationId)
                .entityType(request.entityType())
                .entityId(request.entityId())
                .legalEntityId(request.legalEntityId())
                .templateId(request.templateId())
                .title(request.title().trim())
                .type(request.type())
                .language(request.language())
                .htmlBlobUrl(copied == null ? null : copied.htmlUrl())
                .pdfBlobUrl(copied == null ? null : copied.pdfUrl())
                .createdById(user.getId())
                .updatedById(user.getId())
                .build();
        document = saveOrRemoveNewBlobs(document, copied);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, DOCUMENT_CREATED, document.getId(), organizationId,
                request.templateId(), user.getId());

        return documentMapperHelper.toDetailResponse(organizationId, document);
    }

    /**
     * {@inheritDoc}
     *
     * Full replace of the four fields; the legal entity is checked before the write.
     */
    @Override
    @Transactional
    public DocumentDetailResponse update(UUID organizationId, User user, UUID documentId,
                                         DocumentUpdateRequest request) {
        Document document = documentValidator.ensureDocumentExists(organizationId, documentId);
        verifyOwner(user, organizationId, document.getEntityType(), document.getEntityId(),
                ResourceAccessAction.WRITE);
        legalEntityQueryService.getEntity(organizationId, request.legalEntityId());

        document.setTitle(request.title().trim());
        document.setType(request.type());
        document.setLanguage(request.language());
        document.setLegalEntityId(request.legalEntityId());
        document.setUpdatedById(user.getId());
        // Flushed here so the response carries the new last-change time.
        document = documentRepository.saveAndFlush(document);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, DOCUMENT_UPDATED, documentId, organizationId, user.getId());

        return documentMapperHelper.toDetailResponse(organizationId, document);
    }

    /**
     * {@inheritDoc}
     *
     * The access check runs before the upload, so a refused request stores no blob. The old
     * blobs go only after the row points at the new ones; a failed delete leaves a logged orphan.
     */
    @Override
    @Transactional
    public DocumentDetailResponse replaceFiles(UUID organizationId, User user, UUID documentId, MultipartFile html,
                                               MultipartFile pdf) {
        Document document = documentValidator.ensureDocumentExists(organizationId, documentId);
        verifyOwner(user, organizationId, document.getEntityType(), document.getEntityId(),
                ResourceAccessAction.WRITE);

        String oldHtmlUrl = document.getHtmlBlobUrl();
        String oldPdfUrl = document.getPdfBlobUrl();
        StoredDocumentFiles stored = documentFileStorage.store(html, pdf);

        document.setHtmlBlobUrl(stored.htmlUrl());
        document.setPdfBlobUrl(stored.pdfUrl());
        document.setUpdatedById(user.getId());
        document = saveOrRemoveNewBlobs(document, stored);
        documentFileStorage.deleteQuietly(oldHtmlUrl, oldPdfUrl);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, DOCUMENT_FILES_REPLACED, documentId, organizationId,
                user.getId());

        return documentMapperHelper.toDetailResponse(organizationId, document);
    }

    /**
     * {@inheritDoc}
     *
     * The row is deleted and flushed before the blobs, so a failed delete keeps both. The
     * snapshots hold blobs of their own, so removing the document's blobs never breaks one.
     */
    @Override
    @Transactional
    public void delete(UUID organizationId, User user, UUID documentId) {
        Document document = documentValidator.ensureDocumentExists(organizationId, documentId);
        verifyOwner(user, organizationId, document.getEntityType(), document.getEntityId(),
                ResourceAccessAction.DELETE);
        String htmlUrl = document.getHtmlBlobUrl();
        String pdfUrl = document.getPdfBlobUrl();

        documentRepository.delete(document);
        documentRepository.flush();
        documentFileStorage.deleteQuietly(htmlUrl, pdfUrl);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, DOCUMENT_DELETED, documentId, organizationId);
    }

    /**
     * {@inheritDoc}
     *
     * Order: the entity type, the document within the organization and READ on its owner, its
     * PDF, the mail within the organization and of the same relation; then
     * {@link AttachmentService#storeDocumentSnapshot} checks WRITE on the mail (ADMIN, outreach
     * not sent) and the duplicate rule before it uploads the bytes under a new blob name.
     */
    @Override
    @Transactional
    public AttachmentResponse attachTo(User user, UUID organizationId, UUID documentId, StorageEntityType entityType,
                                       UUID entityId) {
        documentValidator.ensureAttachableEntityType(documentId, entityType);
        Document document = documentValidator.ensureDocumentExists(organizationId, documentId);
        verifyOwner(user, organizationId, document.getEntityType(), document.getEntityId(),
                ResourceAccessAction.READ);
        documentValidator.ensureHasPdf(document);
        Mail mail = mailQueryService.getEntity(organizationId, entityId);
        documentValidator.ensureSameRelation(document, entityId, mail.getRelationId());

        byte[] pdf = documentFileStorage.readPdf(document.getPdfBlobUrl());
        AttachmentContent content = new AttachmentContent(document.getTitle() + DocumentFileConstant.PDF_EXTENSION,
                MediaType.APPLICATION_PDF_VALUE, pdf);
        AttachmentResponse attachment = attachmentService.storeDocumentSnapshot(user, organizationId, entityType,
                entityId, content, documentId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, DOCUMENT_ATTACHED, documentId, organizationId, entityType,
                entityId, attachment.id(), user.getId());

        return attachment;
    }

    /**
     * {@inheritDoc}
     *
     * One DELETE for all rows, then the blobs, best effort. The database sets document_id of
     * their snapshots to null; mail snapshots are removed with their mails by the owner's domain.
     */
    @Override
    @Transactional
    public void deleteDocumentsByEntity(DocumentEntityType entityType, UUID entityId) {
        List<Document> documents = documentRepository.findByEntityTypeAndEntityId(entityType, entityId);
        if (documents.isEmpty()) {
            return;
        }
        documentRepository.deleteAllInBatch(documents);
        for (Document document : documents) {
            documentFileStorage.deleteQuietly(document.getHtmlBlobUrl(), document.getPdfBlobUrl());
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, DOCUMENTS_DELETED_BY_ENTITY, documents.size(), entityType,
                entityId);
    }

    /**
     * Checks the caller's access to the owner with the rules of its access type.
     */
    private void verifyOwner(User user, UUID organizationId, DocumentEntityType entityType, UUID entityId,
                             ResourceAccessAction action) {
        resourceAccessQueryService.verifyResourceAccess(user, organizationId, entityType.getResourceAccessType(),
                entityId, action);
    }

    /**
     * Saves and flushes the row; when that fails, deletes the blobs no row will point at and
     * re-throws, so the request fails without an orphan.
     *
     * @param document the row to write
     * @param newFiles the blobs uploaded for it in this request, or null when none were
     * @return the saved row
     */
    private Document saveOrRemoveNewBlobs(Document document, StoredDocumentFiles newFiles) {
        try {
            return documentRepository.saveAndFlush(document);
        } catch (RuntimeException e) {
            if (newFiles != null) {
                loggingService.error(LOG_PREFIX, LogLayer.SERVICE, FILE_SAVE_FAILED_CLEANUP, e, newFiles.htmlUrl(),
                        newFiles.pdfUrl());
                documentFileStorage.deleteQuietly(newFiles.htmlUrl(), newFiles.pdfUrl());
            }
            throw e;
        }
    }
}
