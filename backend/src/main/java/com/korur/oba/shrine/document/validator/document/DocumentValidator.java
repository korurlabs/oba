package com.korur.oba.shrine.document.validator.document;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.document.constant.document.DocumentConstant;
import com.korur.oba.shrine.document.constant.document.DocumentExceptionLogConstant;
import com.korur.oba.shrine.document.entity.document.Document;
import com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError;
import com.korur.oba.shrine.document.repository.document.DocumentRepository;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

/**
 * Business rules of documents that a request annotation can not check: the lookup within the
 * organization, and the conditions of a PDF snapshot on a mail. Each failure is a client
 * error, logged at warn level and thrown as a business exception for the global handler.
 *
 * Callers: {@link com.korur.oba.shrine.document.service.document.impl.DocumentServiceImpl}
 * and {@link com.korur.oba.shrine.document.service.document.impl.DocumentQueryServiceImpl}.
 */
@Component
@RequiredArgsConstructor
public class DocumentValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DocumentValidator.class);

    private final DocumentRepository documentRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the organization has the document and returns it. A document of another
     * organization answers the same not found as a missing one.
     *
     * @param organizationId the organization the caller acts in
     * @param documentId     the document
     * @return the document, a managed entity of the caller's transaction
     * @throws NotFoundException {@link ExceptionDomainDocumentError#NOT_DOCUMENT_FOUND} (DOC_NOT_002)
     */
    public Document ensureDocumentExists(UUID organizationId, UUID documentId) {
        return documentRepository.findByIdAndOrganizationId(documentId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, DocumentExceptionLogConstant.DOCUMENT_NOT_FOUND,
                            documentId, organizationId);
                    return new NotFoundException(ExceptionDomainDocumentError.NOT_DOCUMENT_FOUND,
                            Map.of(DocumentConstant.FIELD_DOCUMENT_ID, documentId));
                });
    }

    /**
     * Ensures the parent of a snapshot is a mail. {@link StorageEntityType} is shared with
     * every app, so a request could otherwise put a document on a Kazan ticket.
     *
     * @param documentId the document, for the log line
     * @param entityType the kind of parent from the request
     * @throws ValidationException {@link ExceptionValidationError#VALIDATION} with the field entityType
     */
    public void ensureAttachableEntityType(UUID documentId, StorageEntityType entityType) {
        if (entityType != StorageEntityType.MAIL) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, DocumentExceptionLogConstant.ENTITY_TYPE_NOT_ATTACHABLE,
                    documentId, entityType);
            throw new ValidationException(ExceptionValidationError.VALIDATION,
                    Map.of(DocumentConstant.FIELD_ENTITY_TYPE, DocumentConstant.ENTITY_TYPE_NOT_ATTACHABLE_MESSAGE));
        }
    }

    /**
     * Ensures the document has its PDF, which is what the snapshot copies.
     *
     * @param document the document of the organization
     * @throws ValidationException {@link ExceptionDomainDocumentError#VAL_DOCUMENT_WITHOUT_PDF} (DOC_VAL_003)
     */
    public void ensureHasPdf(Document document) {
        if (document.getPdfBlobUrl() == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, DocumentExceptionLogConstant.DOCUMENT_WITHOUT_PDF,
                    document.getId());
            throw new ValidationException(ExceptionDomainDocumentError.VAL_DOCUMENT_WITHOUT_PDF,
                    Map.of(DocumentConstant.FIELD_DOCUMENT_ID, DocumentConstant.DOCUMENT_WITHOUT_PDF_MESSAGE));
        }
    }

    /**
     * Ensures the mail belongs to the relation that owns the document, so a relation never
     * receives another relation's document.
     *
     * @param document       the document, owned by a relation
     * @param mailId         the mail, for the log line
     * @param mailRelationId the relation of the mail
     * @throws ValidationException {@link ExceptionDomainDocumentError#VAL_DOCUMENT_OTHER_RELATION} (DOC_VAL_004)
     */
    public void ensureSameRelation(Document document, UUID mailId, UUID mailRelationId) {
        if (!document.getEntityId().equals(mailRelationId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, DocumentExceptionLogConstant.MAIL_OF_OTHER_RELATION,
                    document.getId(), mailId, document.getEntityId(), mailRelationId);
            throw new ValidationException(ExceptionDomainDocumentError.VAL_DOCUMENT_OTHER_RELATION,
                    Map.of(DocumentConstant.FIELD_ENTITY_ID, DocumentConstant.MAIL_OF_OTHER_RELATION_MESSAGE));
        }
    }
}
