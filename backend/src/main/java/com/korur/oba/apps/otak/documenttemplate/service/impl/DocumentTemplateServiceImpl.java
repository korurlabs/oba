package com.korur.oba.apps.otak.documenttemplate.service.impl;

// 1. Framework-level
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.documenttemplate.constant.service.DocumentTemplateServiceLogConstant.*;

import com.korur.oba.apps.otak.documenttemplate.constant.DocumentTemplateConstant;
import com.korur.oba.apps.otak.documenttemplate.dto.request.DocumentTemplateCreateRequest;
import com.korur.oba.apps.otak.documenttemplate.dto.request.DocumentTemplateUpdateRequest;
import com.korur.oba.apps.otak.documenttemplate.dto.response.DocumentTemplateDetailResponse;
import com.korur.oba.apps.otak.documenttemplate.entity.DocumentTemplate;
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentType;
import com.korur.oba.apps.otak.documenttemplate.file.DocumentFileStorage;
import com.korur.oba.apps.otak.documenttemplate.file.StoredDocumentFiles;
import com.korur.oba.apps.otak.documenttemplate.mapper.DocumentTemplateMapper;
import com.korur.oba.apps.otak.documenttemplate.repository.DocumentTemplateRepository;
import com.korur.oba.apps.otak.documenttemplate.service.DocumentTemplateService;
import com.korur.oba.apps.otak.documenttemplate.validator.DocumentTemplateValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentContent;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

/**
 * Implements the writes to an organization's document templates: create, update, file
 * replacement and delete, and the copy of a template's PDF onto an attachment. The contract and the callers are on
 * {@link DocumentTemplateService}; this class documents how each write is carried out.
 *
 * Access. The organization role is checked once per request by
 * {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} before the
 * controller calls in, so no method here checks roles.
 *
 * Organization scoping. Every existing template is read through
 * {@link DocumentTemplateValidator#ensureTemplateExists}, which looks it up by id and
 * organization together, so a template of another organization is a missing one. A new
 * template takes its organization from the checked request header, never from the body.
 *
 * Business rules. The name rule is checked by {@link DocumentTemplateValidator} before
 * anything is written, so a refused request changes neither the database nor Blob
 * Storage. The database repeats it with a unique index, as a last line of defence.
 *
 * Blob lifecycle. The HTML and PDF files of a template are stored by
 * {@link DocumentFileStorage}. Blob Storage takes no part in the database transaction, so
 * a blob upload is not undone by a rollback and a blob delete is not restored by one.
 * Every write that touches files therefore follows a fixed order: upload the new blobs,
 * write and flush the row, delete the new blobs again if that write fails, and delete
 * the old blobs only after the row no longer points at them. A failure never leaves a row
 * pointing at a missing blob; at worst a blob is orphaned, which is logged at error level
 * with its URL.
 *
 * Transactions. Every public method runs in one read-write transaction
 * ({@link Transactional}).
 *
 * @see com.korur.oba.apps.otak.documenttemplate.service.DocumentTemplateQueryService
 * @see DocumentTemplateMapper
 */
@Service
@RequiredArgsConstructor
public class DocumentTemplateServiceImpl implements DocumentTemplateService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DocumentTemplateServiceImpl.class);

    private final DocumentTemplateRepository documentTemplateRepository;
    private final DocumentTemplateMapper documentTemplateMapper;
    private final DocumentTemplateValidator documentTemplateValidator;
    private final DocumentFileStorage documentFileStorage;
    private final AttachmentService attachmentService;
    private final LoggingService loggingService;

    /**
     * {@inheritDoc}
     *
     * Steps: trim the name and check it is free in the language ({@link DocumentTemplateValidator#ensureNameFree}, with no
     * template excluded), map the request to a new entity
     * ({@link DocumentTemplateMapper#toEntity}), write the name, type and language through
     * {@link #applyContent}, set the organization, save, and map the saved row
     * ({@link DocumentTemplateMapper#toDetailResponse}).
     *
     * No blob is touched: a template is always created without files and receives them
     * through {@link #replaceFiles}.
     *
     * @param organizationId the organization from the checked request header
     * @param user           the authenticated user, recorded in the log line
     * @param request        the type, name and language
     * @return the created template; its file URLs are null
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_DOCUMENT_TEMPLATE_NAME_TAKEN}
     *         (OTK_STA_009) from the name check
     */
    @Override
    @Transactional
    public DocumentTemplateDetailResponse create(UUID organizationId, User user, DocumentTemplateCreateRequest request) {
        // Trimmed before the name check, so " Intro" and "Intro" count as the same name.
        String name = request.name().trim();
        // Null as the excluded id: on create no template may hold the name in the language.
        documentTemplateValidator.ensureNameFree(organizationId, name, request.language(), null);

        // The name, type and language go through applyContent, shared with update, so both
        // writes store the same columns the same way.
        DocumentTemplate template = documentTemplateMapper.toEntity(request);
        applyContent(template, name, request.type(), request.language());
        // The organization comes from the checked header, never from the request body.
        template.setOrganizationId(organizationId);
        template = documentTemplateRepository.save(template);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, TEMPLATE_CREATED, template.getId(), organizationId,
                user.getId());

        return documentTemplateMapper.toDetailResponse(template);
    }

    /**
     * {@inheritDoc}
     *
     * Steps: read the template within the organization
     * ({@link DocumentTemplateValidator#ensureTemplateExists}), trim the name and check it is
     * free in the language with this template excluded
     * ({@link DocumentTemplateValidator#ensureNameFree}), write the name, type and language
     * through {@link #applyContent}, flush, and map the row.
     *
     * Full replace of the fields of the request; the type may change, because the content of
     * every type is the same pair of files. The blob URLs are not touched.
     *
     * @param organizationId the organization from the checked request header
     * @param templateId     the template to update
     * @param request        the new type, name and language
     * @return the updated template, with its new last-change time
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_DOCUMENT_TEMPLATE_FOUND}
     *         (OTK_NOT_007) when the organization has no template with the id
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_DOCUMENT_TEMPLATE_NAME_TAKEN}
     *         (OTK_STA_009) from the name check
     */
    @Override
    @Transactional
    public DocumentTemplateDetailResponse update(UUID organizationId, UUID templateId,
                                                 DocumentTemplateUpdateRequest request) {
        // The organization-scoped lookup: a template of another organization is not found.
        DocumentTemplate template = documentTemplateValidator.ensureTemplateExists(organizationId, templateId);
        String name = request.name().trim();
        // The template itself is excluded, so it may keep its own name.
        documentTemplateValidator.ensureNameFree(organizationId, name, request.language(), templateId);

        applyContent(template, name, request.type(), request.language());
        // Flushed here so the response carries the new last-change time.
        template = documentTemplateRepository.saveAndFlush(template);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, TEMPLATE_UPDATED, templateId, organizationId);

        return documentTemplateMapper.toDetailResponse(template);
    }

    /**
     * {@inheritDoc}
     *
     * Steps, in the order Blob Storage outside the transaction requires:
     * - read the template within the organization
     *   ({@link DocumentTemplateValidator#ensureTemplateExists}) before any upload, so a
     *   refused request stores no blob;
     * - remember the old blob URLs, which the row is about to lose;
     * - upload the new files as new blobs ({@link DocumentFileStorage#store}), which checks
     *   both parts first and cleans up its own partial upload;
     * - point the row at the new blobs and flush; if that write fails, delete the new blobs
     *   again ({@link DocumentFileStorage#deleteQuietly}) and re-throw, so neither an orphan
     *   nor a half-written row stays;
     * - delete the old blobs ({@link DocumentFileStorage#deleteQuietly}), now that no row
     *   points at them. Best effort: a failure is logged as an orphan and the request still
     *   succeeds, because the row change is already done.
     *
     * The new blobs get new names, never the old ones, so the row and the blobs agree at
     * every moment: before the flush the row points at the old blobs, which still exist,
     * and after it at the new ones, which already exist.
     *
     * @param organizationId the organization from the checked request header
     * @param templateId     the template whose files are replaced
     * @param html           the new HTML file
     * @param pdf            the new PDF file rendered from it
     * @return the updated template, with signed URLs of the new files
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_DOCUMENT_TEMPLATE_FOUND}
     *         (OTK_NOT_007) when the organization has no template with the id
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_FILE_NOT_HTML_OR_PDF}
     *         (OTK_VAL_009) from {@link DocumentFileStorage#store} when a part is missing, empty or of the
     *         wrong type
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.IntegrationErrorException
     *         {@link com.korur.oba.connect.azure.exception.ExceptionDomainAzureError#UPS_AZURE_BLOB_UPLOAD}
     *         (AZR_UPS_001) from {@link DocumentFileStorage#store} when a file can not be read or uploaded
     */
    @Override
    @Transactional
    public DocumentTemplateDetailResponse replaceFiles(UUID organizationId, UUID templateId, MultipartFile html,
                                                       MultipartFile pdf) {
        // Checked before the upload, so a missing template stores no blob.
        DocumentTemplate template = documentTemplateValidator.ensureTemplateExists(organizationId, templateId);

        // Kept for the last step: these blobs are deleted only once the row points elsewhere.
        // They are null on the first upload; deleteQuietly skips null URLs.
        String oldHtmlUrl = template.getHtmlBlobUrl();
        String oldPdfUrl = template.getPdfBlobUrl();
        // Uploads under fresh names; a failure here leaves no blob and no row change behind.
        StoredDocumentFiles stored = documentFileStorage.store(html, pdf);

        template.setHtmlBlobUrl(stored.htmlUrl());
        template.setPdfBlobUrl(stored.pdfUrl());
        try {
            // Flushed inside the try, so a failed write is caught here while the new blobs can still be removed.
            template = documentTemplateRepository.saveAndFlush(template);
        } catch (RuntimeException e) {
            // No row will point at the new blobs once the transaction rolls back, so they are
            // removed here; the error is re-thrown so the request fails and the old row stays.
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, FILE_SAVE_FAILED_CLEANUP, e, stored.htmlUrl(),
                    stored.pdfUrl());
            documentFileStorage.deleteQuietly(stored.htmlUrl(), stored.pdfUrl());
            throw e;
        }
        // The row now points at the new blobs, so the old ones can go. A failed delete only
        // leaves a logged orphan; it does not fail the request.
        documentFileStorage.deleteQuietly(oldHtmlUrl, oldPdfUrl);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, TEMPLATE_FILES_REPLACED, templateId, organizationId);

        // The mapper signs the new unsigned URLs, so the client can open the files at once.
        return documentTemplateMapper.toDetailResponse(template);
    }

    /**
     * {@inheritDoc}
     *
     * Steps: read the template within the organization
     * ({@link DocumentTemplateValidator#ensureTemplateExists}), remember its blob URLs,
     * delete the row and flush, then delete the blobs
     * ({@link DocumentFileStorage#deleteQuietly}).
     *
     * The flush makes the database apply the delete before any blob is removed. If the row
     * delete fails, the transaction rolls back with the blobs untouched. The blobs belong to
     * this template alone, because an attachment copies the PDF into a blob of its own, so
     * deleting them never breaks an attachment.
     *
     * @param organizationId the organization from the checked request header
     * @param templateId     the template to delete
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_DOCUMENT_TEMPLATE_FOUND}
     *         (OTK_NOT_007) when the organization has no template with the id
     */
    @Override
    @Transactional
    public void delete(UUID organizationId, UUID templateId) {
        DocumentTemplate template = documentTemplateValidator.ensureTemplateExists(organizationId, templateId);
        // Read before the delete; both are null for a template without files.
        String htmlUrl = template.getHtmlBlobUrl();
        String pdfUrl = template.getPdfBlobUrl();

        // The row goes first and is flushed, so a failed delete keeps both the row and its blobs.
        documentTemplateRepository.delete(template);
        documentTemplateRepository.flush();
        // Best effort: the row is gone, so a failed blob delete leaves a logged orphan only.
        documentFileStorage.deleteQuietly(htmlUrl, pdfUrl);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, TEMPLATE_DELETED, templateId, organizationId);
    }

    /**
     * {@inheritDoc}
     *
     * The entity type is checked first, so a request for another app's parent reads nothing.
     * The template is read through {@link DocumentTemplateValidator#ensureTemplateExists}, so a
     * template of another organization answers not found, and
     * {@link DocumentTemplateValidator#ensureTemplateReady} requires its PDF. The PDF is read by
     * {@link DocumentFileStorage#readPdf} and handed to
     * {@link AttachmentService#storeAttachmentContent}, which checks WRITE on the parent before
     * it uploads the bytes under a new blob name. That call joins this transaction, so the row
     * lock it takes on a mail's outreach is held until this method returns.
     */
    @Override
    @Transactional
    public AttachmentResponse copyToAttachment(User user, UUID organizationId, UUID templateId,
                                               StorageEntityType entityType, UUID entityId) {
        documentTemplateValidator.ensureAttachableEntityType(templateId, entityType);
        DocumentTemplate template = documentTemplateValidator.ensureTemplateExists(organizationId, templateId);
        documentTemplateValidator.ensureTemplateReady(template);

        byte[] pdf = documentFileStorage.readPdf(template.getPdfBlobUrl());
        // Named after the template, so the contact sees the same file name the template list shows.
        AttachmentContent content = new AttachmentContent(template.getName() + DocumentTemplateConstant.PDF_EXTENSION,
                MediaType.APPLICATION_PDF_VALUE, pdf);
        AttachmentResponse attachment = attachmentService.storeAttachmentContent(user, organizationId, entityType,
                entityId, content);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, TEMPLATE_COPIED_TO_ATTACHMENT, templateId, organizationId,
                entityType, entityId, attachment.id(), user.getId());

        return attachment;
    }

    /**
     * Writes the trimmed name, the type and the language, shared by {@link #create} and
     * {@link #update} so both store the same columns the same way. Every field is written,
     * so an update is a full replace.
     *
     * @param template the entity to write to, new or managed
     * @param name     the name, already trimmed
     * @param type     the kind of document
     * @param language the language of the content
     */
    private void applyContent(DocumentTemplate template, String name, DocumentType type, DocumentLanguage language) {
        template.setName(name);
        template.setType(type);
        template.setLanguage(language);
    }
}
