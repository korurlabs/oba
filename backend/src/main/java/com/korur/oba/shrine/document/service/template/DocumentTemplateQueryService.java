package com.korur.oba.shrine.document.service.template;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.dto.request.template.DocumentTemplateQueryRequest;
import com.korur.oba.shrine.document.dto.response.template.DocumentTemplateDetailResponse;
import com.korur.oba.shrine.document.dto.response.template.DocumentTemplateListResponse;
import com.korur.oba.shrine.document.entity.template.DocumentTemplate;

/**
 * Read side of the document template domain: the template list, the template detail and
 * the template entity that another domain copies content from.
 *
 * A document template is a reusable document of one organization (a proposal, an NDA),
 * see {@link DocumentTemplate}. Every read is scoped to the
 * organization the caller acts in. That organization comes from the request's checked
 * organization header, never from a body, and a template of another organization is
 * answered exactly like a template that does not exist, so a foreign id reveals nothing.
 *
 * Writes (create, update, file replacement, delete) live in the command side,
 * {@link DocumentTemplateService}. This split keeps every read in a read-only
 * transaction.
 *
 * Callers:
 * - {@link com.korur.oba.shrine.document.controller.template.DocumentTemplateController} for
 *   the list and detail endpoints under /document-templates, open to every organization
 *   MEMBER.
 * - other domains, through {@link #getEntity(UUID, UUID)}. They read templates through this
 *   interface and never through
 *   {@link com.korur.oba.shrine.document.repository.template.DocumentTemplateRepository}.
 *
 * @see com.korur.oba.shrine.document.service.template.impl.DocumentTemplateQueryServiceImpl
 * @see DocumentTemplateService
 */
public interface DocumentTemplateQueryService {

    /**
     * Lists one page of the organization's templates, sorted by name, then language, then
     * id.
     *
     * The order is fixed on purpose: the language next to the name puts the translations
     * of one template (the same name in NL, TR and EN) next to each other, and the id as
     * the last key keeps the pages stable, so no row is shown twice or skipped while the
     * user pages. The sortBy and sortDirection of the request are therefore ignored.
     *
     * The rows are {@link DocumentTemplateListResponse} items: no file URL, only the name, type,
     * language, legal entity id and short name, the derived ready flag
     * ({@link DocumentTemplate#isReady()}) and the last change time. The signed file URLs
     * are only in the detail, see {@link #get(UUID, UUID)}.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.controller.template.DocumentTemplateController#listDocumentTemplates}.
     * The filters are built by
     * {@link com.korur.oba.shrine.document.specification.template.DocumentTemplateSpecification#filterBy}.
     *
     * @param organizationId the organization the caller acts in; only its templates are listed
     * @param request        the one-based page and page size, the optional name search and the optional
     *                       type, language and legal entity filters; the legal entity filter keeps the templates
     *                       that legal entity may use, its own and those without a legal entity
     * @return the page of list items; an empty page when nothing matches, never an error
     */
    Page<DocumentTemplateListResponse> list(UUID organizationId, DocumentTemplateQueryRequest request);

    /**
     * Reads one template of the organization with its full content.
     *
     * The content is the HTML and the PDF file, whose URLs are stored unsigned and answered signed: the blob container is
     * private, so the mapper adds a fresh Shared Access Signature on every read (see
     * {@link com.korur.oba.shrine.document.mapper.template.DocumentTemplateMapper#toDetailResponse}).
     * Such a URL works only for the signature lifetime configured in
     * {@link com.korur.oba.connect.azure.properties.AzureBlobStorageProperties} (30 minutes
     * by default); a client that needs the file later reads the detail again.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.controller.template.DocumentTemplateController#getDocumentTemplate}.
     *
     * @param organizationId the organization the caller acts in
     * @param templateId     the template identifier
     * @return the template detail, with signed file URLs once the template has files
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#NOT_DOCUMENT_TEMPLATE_FOUND}
     *         (DOC_NOT_001) when the organization has no template with the id, also when the id belongs
     *         to another organization
     */
    DocumentTemplateDetailResponse get(UUID organizationId, UUID templateId);

    /**
     * Reads one template entity of the organization, for another domain that copies its
     * content.
     *
     * The entity is returned rather than a DTO because a caller that reads the files needs
     * the unsigned blob URLs, and a signed URL would expire. The caller must not change the
     * entity.
     *
     * @param organizationId the organization the caller acts in
     * @param templateId     the template identifier
     * @return the template entity
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#NOT_DOCUMENT_TEMPLATE_FOUND}
     *         (DOC_NOT_001) when the organization has no template with the id, also when the id belongs
     *         to another organization
     */
    DocumentTemplate getEntity(UUID organizationId, UUID templateId);
}
