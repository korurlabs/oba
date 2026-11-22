package com.korur.oba.shrine.document.service.document;

// 1. Framework-level
import org.springframework.web.multipart.MultipartFile;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.dto.request.document.DocumentCreateRequest;
import com.korur.oba.shrine.document.dto.request.document.DocumentUpdateRequest;
import com.korur.oba.shrine.document.dto.response.document.DocumentDetailResponse;
import com.korur.oba.shrine.document.enums.document.DocumentEntityType;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

/**
 * Write side of the document domain: create, update, file replacement, delete, the PDF
 * snapshot on a mail and the cascade when an owner is deleted.
 *
 * Rules every method keeps:
 * - Access. The endpoint needs MEMBER; the rest is access to the owner, checked through
 *   {@link com.korur.oba.shrine.access.service.ResourceAccessQueryService#verifyResourceAccess}
 *   with the access type of {@link DocumentEntityType}: WRITE to create, change or replace
 *   files, DELETE to delete.
 * - Organization scoping. The organization comes from the checked header; a document, legal
 *   entity or template of another organization answers 404 like a missing one.
 * - Authors. Create sets the creator and the last editor to the caller; every later write sets
 *   the last editor.
 * - Blob lifecycle. New blobs are uploaded before the row points at them and old blobs are
 *   deleted only after it stopped pointing at them, as for templates.
 * - Transactions. Every method runs in one read-write transaction.
 *
 * Callers: {@link com.korur.oba.shrine.document.controller.document.DocumentController} and,
 * for {@link #deleteDocumentsByEntity},
 * {@link com.korur.oba.apps.otak.relation.service.impl.RelationServiceImpl#delete}.
 *
 * @see DocumentQueryService
 */
public interface DocumentService {

    /**
     * Creates a document of an owner. With a template, the template's files are copied to new
     * blobs; a template without files, or no template, gives a document without files.
     *
     * @param organizationId the organization from the checked request header
     * @param user           the caller, recorded as creator and last editor
     * @param request        the owner, legal entity, fields and optional template
     * @return the created document
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException the owner's not found
     *         (OTK_NOT_001 for a relation), LGE_NOT_001 or DOC_NOT_001 for the template
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException when the caller may not write the
     *         owner (APP_SEC_001, ORG_SEC_003) or the owner belongs to another organization (APP_SEC_002)
     */
    DocumentDetailResponse create(UUID organizationId, User user, DocumentCreateRequest request);

    /**
     * Replaces the title, type, language and legal entity of a document; the files stay.
     *
     * @param organizationId the organization from the checked request header
     * @param user           the caller, recorded as last editor
     * @param documentId     the document
     * @param request        the new fields
     * @return the updated document
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException DOC_NOT_002 or LGE_NOT_001
     */
    DocumentDetailResponse update(UUID organizationId, User user, UUID documentId, DocumentUpdateRequest request);

    /**
     * Replaces both files of a document and deletes the old blobs afterwards. Snapshots made
     * earlier keep their own copy.
     *
     * @param organizationId the organization from the checked request header
     * @param user           the caller, recorded as last editor
     * @param documentId     the document
     * @param html           the new HTML file
     * @param pdf            the PDF rendered from it
     * @return the updated document, with signed URLs of the new files
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException DOC_NOT_002
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException DOC_VAL_001 for a bad part
     */
    DocumentDetailResponse replaceFiles(UUID organizationId, User user, UUID documentId, MultipartFile html,
                                        MultipartFile pdf);

    /**
     * Deletes a document and its blobs. Its snapshots stay; the database sets their
     * document_id to null.
     *
     * @param organizationId the organization from the checked request header
     * @param user           the caller, whose DELETE on the owner is checked
     * @param documentId     the document
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException DOC_NOT_002
     */
    void delete(UUID organizationId, User user, UUID documentId);

    /**
     * Stores the document's current PDF as a new attachment of a mail of the same relation,
     * with document_id set. The bytes go to a blob of their own, so a later upload to the
     * document never changes the snapshot. The file name is the title plus .pdf.
     *
     * @param user           the caller
     * @param organizationId the organization from the checked request header
     * @param documentId     the document
     * @param entityType     the kind of parent; only MAIL
     * @param entityId       the mail
     * @return the new attachment
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException GEN_VAL_001 for another
     *         entity type, DOC_VAL_003 without a PDF, DOC_VAL_004 for a mail of another relation, STG_VAL_005
     *         when the mail already holds the same PDF
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException APP_SEC_002 for a mail of another
     *         organization, or no read access to the document's owner or the mail
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException DOC_NOT_002 or OTK_NOT_011
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException OTK_STA_011 when the mail's
     *         outreach is sent
     */
    AttachmentResponse attachTo(User user, UUID organizationId, UUID documentId, StorageEntityType entityType,
                                UUID entityId);

    /**
     * Deletes every document of an owner and their blobs, as a side effect of the owner's
     * delete. The owner's domain has proved the organization, so no access check runs; blob
     * deletes are best effort and never fail the owner's delete.
     *
     * @param entityType the kind of owner
     * @param entityId   the owner
     */
    void deleteDocumentsByEntity(DocumentEntityType entityType, UUID entityId);
}
