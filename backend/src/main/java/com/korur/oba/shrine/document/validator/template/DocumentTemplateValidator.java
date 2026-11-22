package com.korur.oba.shrine.document.validator.template;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.document.constant.template.DocumentTemplateConstant;
import com.korur.oba.shrine.document.constant.template.DocumentTemplateExceptionLogConstant;
import com.korur.oba.shrine.document.entity.template.DocumentTemplate;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError;
import com.korur.oba.shrine.document.repository.template.DocumentTemplateRepository;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

/**
 * Business rules of document templates that a request annotation can not check, because
 * they need the database or depend on another field:
 * - the lookup of one template within the caller's organization ({@link #ensureTemplateExists});
 * - the name rule: unique per organization and language, case ignored ({@link #ensureNameFree});
 * - a copy onto an attachment needs the template's PDF ({@link #ensureTemplateReady}) and a
 *   mail or a relation as the parent ({@link #ensureAttachableEntityType}).
 *
 * The database enforces the name rule too, with a unique index on shrine.document_templates.
 * The check here runs first so the client gets a specific error code and the fields that
 * are wrong, instead of a generic database error.
 *
 * Each method throws a business exception that the global exception handler turns into
 * the error response; failures are logged at warn level here, because they are client
 * errors, not faults. The validator runs inside the caller's transaction.
 *
 * Callers:
 * - {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl}
 *   on create, update, file replacement, delete and the copy onto an attachment;
 * - {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateQueryServiceImpl}
 *   for the detail and for {@link com.korur.oba.shrine.document.service.template.DocumentTemplateQueryService#getEntity}.
 *
 * @see DocumentTemplateRepository
 * @see DocumentTemplate
 */
@Component
@RequiredArgsConstructor
public class DocumentTemplateValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DocumentTemplateValidator.class);

    private final DocumentTemplateRepository documentTemplateRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the organization has the template and returns it. A template of another
     * organization answers the same not found as a missing one, so its id reveals nothing
     * to another tenant.
     *
     * This is the single lookup of one template by id: every read and write of a template,
     * and every copy onto an attachment, passes through it, so none can reach a template of
     * another organization. It reads through
     * {@link DocumentTemplateRepository#findByIdAndOrganizationId}.
     *
     * Called by {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl}
     * (update, replaceFiles, delete, copyToAttachment) and by
     * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateQueryServiceImpl}
     * (get, getEntity).
     *
     * @param organizationId the organization the caller acts in
     * @param templateId     the template identifier
     * @return the template, a managed entity of the caller's transaction
     * @throws NotFoundException {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#NOT_DOCUMENT_TEMPLATE_FOUND} (DOC_NOT_001)
     *         if the organization has no template with the id, also when the id belongs to another
     *         organization
     */
    public DocumentTemplate ensureTemplateExists(UUID organizationId, UUID templateId) {
        return documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId)
                .orElseThrow(() -> {
                    // Logged with both ids, so a probe for another tenant's template shows in the logs.
                    loggingService.warn(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            DocumentTemplateExceptionLogConstant.TEMPLATE_NOT_FOUND,
                            templateId,
                            organizationId);
                    // The response names only the template id, never the organization that owns it.
                    return new NotFoundException(
                            ExceptionDomainDocumentError.NOT_DOCUMENT_TEMPLATE_FOUND,
                            Map.of(DocumentTemplateConstant.FIELD_TEMPLATE_ID, templateId));
                });
    }

    /**
     * Ensures no other template of the organization has the name in the language, case
     * ignored, so a template can be picked by its name and language without ambiguity.
     * The same name may exist once per language, which is how a template is translated.
     *
     * On create no template is excluded and the check reads
     * {@link DocumentTemplateRepository#existsByOrganizationIdAndNameIgnoreCaseAndLanguage}.
     * On update the template itself is excluded, so it may keep its name, and the check reads
     * {@link DocumentTemplateRepository#existsByOrganizationIdAndNameIgnoreCaseAndLanguageAndIdNot}.
     * Two requests that race past this check are still stopped by the unique index.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl}
     * on create and update.
     *
     * @param organizationId   the organization that owns the templates
     * @param name             the trimmed name to check
     * @param language         the language the name must be unique in
     * @param exceptTemplateId the template that may keep its own name on an update, or null on create
     * @throws ConflictException {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#STA_DOCUMENT_TEMPLATE_NAME_TAKEN}
     *         (DOC_STA_001) when another template of the organization has the name in the language
     */
    public void ensureNameFree(UUID organizationId, String name, DocumentLanguage language, UUID exceptTemplateId) {
        // A null exceptTemplateId means create: no row may hold the name. Otherwise every row
        // but the one being updated is checked.
        boolean taken = exceptTemplateId == null
                ? documentTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguage(
                        organizationId, name, language)
                : documentTemplateRepository.existsByOrganizationIdAndNameIgnoreCaseAndLanguageAndIdNot(
                        organizationId, name, language, exceptTemplateId);
        if (taken) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    DocumentTemplateExceptionLogConstant.TEMPLATE_NAME_TAKEN, organizationId, name, language);
            // The details name both fields, so the form can mark the name and language together.
            throw new ConflictException(
                    ExceptionDomainDocumentError.STA_DOCUMENT_TEMPLATE_NAME_TAKEN,
                    Map.of(DocumentTemplateConstant.FIELD_NAME, name,
                            DocumentTemplateConstant.FIELD_LANGUAGE, language));
        }
    }

    /**
     * Ensures a template can be copied onto an attachment: it has its PDF, because the PDF is
     * what the mail or the relation gets. The HTML is not needed for the copy.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl#copyToAttachment}
     * before the PDF is read.
     *
     * @param template the template of the organization
     * @throws ValidationException {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#VAL_DOCUMENT_NOT_USABLE} (DOC_VAL_002)
     *         when the template has no PDF yet
     */
    public void ensureTemplateReady(DocumentTemplate template) {
        if (template.getPdfBlobUrl() == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, DocumentTemplateExceptionLogConstant.TEMPLATE_NOT_USABLE,
                    template.getId(), DocumentTemplateConstant.TEMPLATE_WITHOUT_PDF_MESSAGE);
            // The reason goes under templateId, because the template is what the caller has to change.
            throw new ValidationException(ExceptionDomainDocumentError.VAL_DOCUMENT_NOT_USABLE,
                    Map.of(DocumentTemplateConstant.FIELD_TEMPLATE_ID,
                            DocumentTemplateConstant.TEMPLATE_WITHOUT_PDF_MESSAGE));
        }
    }

    /**
     * Ensures the parent of the new attachment is a mail or a relation, the only Otak parents
     * a template belongs on. {@link StorageEntityType} is shared with every app, so a request
     * could otherwise copy an Otak template onto a Kazan ticket.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl#copyToAttachment}
     * before the template is read.
     *
     * @param templateId the template to copy, for the log line
     * @param entityType the kind of parent from the request
     * @throws ValidationException {@link ExceptionValidationError#VALIDATION} with the field entityType
     *         when the type is neither MAIL nor RELATION
     */
    public void ensureAttachableEntityType(UUID templateId, StorageEntityType entityType) {
        if (entityType != StorageEntityType.MAIL && entityType != StorageEntityType.RELATION) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    DocumentTemplateExceptionLogConstant.ENTITY_TYPE_NOT_ATTACHABLE, templateId, entityType);
            throw new ValidationException(ExceptionValidationError.VALIDATION,
                    Map.of(DocumentTemplateConstant.FIELD_ENTITY_TYPE,
                            DocumentTemplateConstant.ENTITY_TYPE_NOT_ATTACHABLE_MESSAGE));
        }
    }
}
