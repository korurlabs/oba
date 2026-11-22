package com.korur.oba.shrine.document.service.template;

// 1. Framework-level
import org.springframework.web.multipart.MultipartFile;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.dto.request.template.DocumentTemplateCreateRequest;
import com.korur.oba.shrine.document.dto.request.template.DocumentTemplateUpdateRequest;
import com.korur.oba.shrine.document.dto.response.template.DocumentTemplateDetailResponse;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

/**
 * Write side of the document template domain: create, update, file replacement and
 * delete of an organization's templates, and the copy of a template's PDF onto a mail or a
 * relation as an attachment.
 *
 * A document template is a reusable document of one organization (a proposal, an NDA),
 * see {@link com.korur.oba.shrine.document.entity.template.DocumentTemplate}.
 * A user copies a template's PDF onto a mail or a relation; the attachment is a copy with a
 * blob of its own, not a reference, so nothing done here ever changes an attachment that was
 * already made.
 *
 * Reads live in the query side, {@link DocumentTemplateQueryService}; the implementation
 * is {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl}.
 *
 * Callers: {@link com.korur.oba.shrine.document.controller.template.DocumentTemplateController}
 * for the endpoints under /document-templates, and
 * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#create}, which copies
 * the templates of a new MAIL outreach onto its mail through {@link #copyToAttachment}. No other
 * domain writes templates.
 *
 * Rules every method keeps:
 * - Access. The caller's role is checked before these methods run, by
 *   {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} on the
 *   controller endpoint. The service does not check roles again.
 * - Organization scoping. The organization comes from the checked X-Organization-ID
 *   header, never from a body. Every template is read through
 *   {@link com.korur.oba.shrine.document.validator.template.DocumentTemplateValidator#ensureTemplateExists},
 *   so a template of another organization answers the same not found as a missing one and
 *   its id reveals nothing to another tenant.
 * - Content. Every template holds an HTML file and the PDF rendered from it, whatever its
 *   type, so the type may change on update.
 * - Legal entity. A template may name a legal entity of its organization; null means every
 *   legal entity may use it. A given legal entity is checked through
 *   {@link com.korur.oba.shrine.legalentity.service.LegalEntityQueryService#getEntity}, so one
 *   of another organization answers not found.
 * - Authors. Create records the caller as creator and last editor; update and file
 *   replacement record the caller as last editor.
 * - Blob lifecycle. The files live in the private blob container, handled by
 *   {@link com.korur.oba.shrine.document.helper.common.DocumentFileStorage}. Blob Storage
 *   takes no part in the database transaction, so new blobs are uploaded before the row
 *   points at them and old blobs are deleted only after the row stopped pointing at them.
 *   A failure therefore never leaves a row pointing at a missing blob; at worst a blob is
 *   orphaned, which is logged.
 * - Transactions. Every method runs in one read-write transaction.
 *
 * @see DocumentTemplateQueryService
 */
public interface DocumentTemplateService {

    /**
     * Creates a template of the organization, without files.
     *
     * A new template has no files yet, so it is not ready for use (see
     * {@link com.korur.oba.shrine.document.entity.template.DocumentTemplate#isReady()}) until
     * {@link #replaceFiles(UUID, User, UUID, MultipartFile, MultipartFile)} uploads them. Files do
     * not come with the create, because a JSON body and two binary parts are two different
     * request shapes, and a template without files is a valid draft.
     *
     * The name is stored trimmed and must be free in the language within the organization,
     * case ignored, so a template can be picked by name and language without ambiguity; the
     * same name may exist once per language, which is how a template is translated.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.controller.template.DocumentTemplateController#createDocumentTemplate}.
     *
     * @param organizationId the organization the caller acts in, which owns the new template
     * @param user           the authenticated user, recorded as creator and last editor
     * @param request        the type, name, language and optional legal entity
     * @return the created template; its file URLs are null
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#STA_DOCUMENT_TEMPLATE_NAME_TAKEN}
     *         (DOC_STA_001) when another template of the organization has the name in the language
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.shrine.legalentity.exception.ExceptionDomainLegalEntityError#NOT_LEGAL_ENTITY_FOUND}
     *         (LGE_NOT_001) when the legal entity is not one of the organization
     * @see com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl#create
     */
    DocumentTemplateDetailResponse create(UUID organizationId, User user, DocumentTemplateCreateRequest request);

    /**
     * Replaces the type, name, language and legal entity of a template.
     *
     * This is a full replace, as every PUT of this API: every field of the request is
     * written. The client sends the whole template it shows, and the stored row then equals
     * it, without a merge rule for absent fields. The type may change, because every type
     * holds the same pair of files. The files stay as they are: they change only
     * through {@link #replaceFiles(UUID, User, UUID, MultipartFile, MultipartFile)}.
     *
     * Attachments already copied from the template keep their own content: they are copies,
     * not references.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.controller.template.DocumentTemplateController#updateDocumentTemplate}.
     *
     * @param organizationId the organization the caller acts in, which owns the template
     * @param user           the authenticated user, recorded as last editor
     * @param templateId     the template to update
     * @param request        the new type, name, language and legal entity
     * @return the updated template, with its new last-change time and signed file URLs
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#NOT_DOCUMENT_TEMPLATE_FOUND}
     *         (DOC_NOT_001) when the organization has no template with the id
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#STA_DOCUMENT_TEMPLATE_NAME_TAKEN}
     *         (DOC_STA_001) when another template of the organization has the name in the language
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.shrine.legalentity.exception.ExceptionDomainLegalEntityError#NOT_LEGAL_ENTITY_FOUND}
     *         (LGE_NOT_001) when the legal entity is not one of the organization
     * @see com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl#update
     */
    DocumentTemplateDetailResponse update(UUID organizationId, User user, UUID templateId,
                                          DocumentTemplateUpdateRequest request);

    /**
     * Replaces both files of a template: the HTML source and the PDF rendered from it. The old blobs are deleted afterwards.
     *
     * Both parts are required together, because the PDF is rendered from the HTML and the
     * two must always match; a single-file update could leave a PDF that shows other text
     * than its HTML. After the first upload the template is ready for use and its PDF can be
     * copied onto attachments.
     *
     * The order follows from Blob Storage being outside the database transaction:
     * - upload the new blobs through
     *   {@link com.korur.oba.shrine.document.helper.common.DocumentFileStorage#store},
     *   which checks both parts first and leaves no blob behind when it fails;
     * - point the row at the new blobs and flush it; when that write fails, delete the new
     *   blobs again, because no row will ever point at them;
     * - only then delete the old blobs through
     *   {@link com.korur.oba.shrine.document.helper.common.DocumentFileStorage#deleteQuietly},
     *   best effort, so a failed delete leaves a logged orphan and never fails the request.
     * Deleting the old blobs first would leave the row pointing at missing files if the
     * upload or the write then failed.
     *
     * Attachments already copied from the template keep their own copies of the old PDF:
     * a copy has its own blob, so deleting the template's old blobs never touches it.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.controller.template.DocumentTemplateController#replaceDocumentTemplateFiles}.
     *
     * @param organizationId the organization the caller acts in, which owns the template
     * @param user           the authenticated user, recorded as last editor
     * @param templateId     the template whose files are replaced
     * @param html           the new HTML file, a non-empty text/html part
     * @param pdf            the new PDF file rendered from it, a non-empty application/pdf part
     * @return the updated template, with signed URLs of the new files
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#NOT_DOCUMENT_TEMPLATE_FOUND}
     *         (DOC_NOT_001) when the organization has no template with the id
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#VAL_FILE_NOT_HTML_OR_PDF}
     *         (DOC_VAL_001) when a part is missing, empty or not an HTML or PDF file
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.IntegrationErrorException
     *         {@link com.korur.oba.connect.azure.exception.ExceptionDomainAzureError#UPS_AZURE_BLOB_UPLOAD}
     *         (AZR_UPS_001) when a file can not be read or uploaded
     * @see com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl#replaceFiles
     */
    DocumentTemplateDetailResponse replaceFiles(UUID organizationId, User user, UUID templateId, MultipartFile html,
                                                MultipartFile pdf);

    /**
     * Deletes a template and its blobs.
     *
     * Attachments copied from the template stay: each holds its own blob, because an
     * attachment is a copy, not a reference. What a relation received never changes because a
     * template was removed.
     *
     * The row is deleted and flushed before the blobs, because Blob Storage is outside the
     * database transaction: a failed row delete keeps both the row and its files, and a
     * failed blob delete after it only leaves a logged orphan, never a row without its files.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.controller.template.DocumentTemplateController#deleteDocumentTemplate}.
     *
     * @param organizationId the organization the caller acts in, which owns the template
     * @param templateId     the template to delete
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#NOT_DOCUMENT_TEMPLATE_FOUND}
     *         (DOC_NOT_001) when the organization has no template with the id
     * @see com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl#delete
     */
    void delete(UUID organizationId, UUID templateId);

    /**
     * Copies the PDF of a template onto a mail or a relation as a new attachment, named after
     * the template with the .pdf extension and stored under a blob of its own, so replacing or
     * deleting the template later leaves the attachment intact.
     *
     * Steps, in this order: the entity type must be MAIL or RELATION; the template is read
     * within the organization and must have its PDF; the PDF is read from the private blob
     * container; then
     * {@link com.korur.oba.shrine.storage.service.attachment.AttachmentService#storeAttachmentContent}
     * checks WRITE on the parent (its organization, the caller's role, and for a mail that its
     * outreach is not SENT) and the duplicate rule, and stores the bytes.
     *
     * Called only by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#create},
     * which copies the templates of a new MAIL outreach onto its mail. No endpoint offers the
     * copy any more; a document's PDF snapshot on a mail replaces it.
     *
     * @param user           the authenticated user, whose access to the parent is checked
     * @param organizationId the organization the caller acts in, which owns the template and the parent
     * @param templateId     the template to copy
     * @param entityType     the kind of parent, MAIL or RELATION
     * @param entityId       the mail or relation that gets the attachment
     * @return the new attachment
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.dojo.exception.enums.ExceptionValidationError#VALIDATION} with the field
     *         entityType when it is neither MAIL nor RELATION,
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#VAL_DOCUMENT_NOT_USABLE}
     *         (DOC_VAL_002) when the template has no PDF, or
     *         {@link com.korur.oba.shrine.storage.exception.ExceptionDomainStorageError#VAL_FILE_ALREADY_EXISTS}
     *         (STG_VAL_005) when the parent already holds the same PDF
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#NOT_DOCUMENT_TEMPLATE_FOUND}
     *         (DOC_NOT_001), {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_FOUND}
     *         (OTK_NOT_011) or {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001) when the template or the parent does not exist
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_OUTREACH_NOT_EDITABLE}
     *         (OTK_STA_011) when the mail's outreach is SENT
     */
    AttachmentResponse copyToAttachment(User user, UUID organizationId, UUID templateId, StorageEntityType entityType,
                                        UUID entityId);
}
