package com.korur.oba.shrine.document.controller.template;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;

import jakarta.validation.Valid;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.document.constant.common.DocumentFileConstant;
import com.korur.oba.shrine.document.constant.controller.template.DocumentTemplateControllerLogConstant;
import com.korur.oba.shrine.document.constant.controller.template.DocumentTemplateControllerResponseConstant;
import com.korur.oba.shrine.document.dto.request.template.DocumentTemplateCreateRequest;
import com.korur.oba.shrine.document.dto.request.template.DocumentTemplateQueryRequest;
import com.korur.oba.shrine.document.dto.request.template.DocumentTemplateUpdateRequest;
import com.korur.oba.shrine.document.dto.response.template.DocumentTemplateDetailResponse;
import com.korur.oba.shrine.document.dto.response.template.DocumentTemplateListResponse;
import com.korur.oba.shrine.document.service.template.DocumentTemplateQueryService;
import com.korur.oba.shrine.document.service.template.DocumentTemplateService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * REST controller under /document-templates for the document templates of an
 * organization: reusable proposals, NDAs, contracts and other documents, each an HTML file
 * and the PDF rendered from it. Templates are organization-wide, so they live in shrine and
 * not under an app path. Reusable mails are Otak's mail templates, under /otak/mail-templates.
 *
 * Access. Every endpoint carries {@link RequiredUserAccess} with its minimum
 * {@link OrganizationMemberRole}, and
 * {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} checks it
 * against the caller's membership in the organization of the X-Organization-ID header
 * before the method runs. Every member reads templates ({@link OrganizationMemberRole#MEMBER});
 * only an admin or owner creates, changes or deletes one or uploads its files
 * ({@link OrganizationMemberRole#ADMIN}), because templates speak for the organization. No
 * app grant is checked: the controller is outside the apps packages. The checked header is
 * passed to the services as the organization id, and every lookup is scoped to it, so a
 * template or legal entity of another organization answers 404 like a missing one.
 *
 * Content. A template is created as JSON without content and receives its HTML file and
 * the PDF rendered from it through the separate multipart endpoint
 * {@link #replaceDocumentTemplateFiles}; until then it is not ready.
 *
 * Each method only logs, delegates to {@link DocumentTemplateService} for writes or
 * {@link DocumentTemplateQueryService} for reads, and wraps the result in a
 * {@link CustomResponse} through {@link ResponseUtil}. Errors are thrown by the services
 * and turned into the error response, with its error code, by
 * {@link com.korur.oba.dojo.exception.handler.mvc_layer.GlobalExceptionHandler}.
 *
 * Clients: the template pages of the Otak web app and the oba CLI.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/document-templates")
@Tag(name = "Document Templates", description = "Reusable document templates of an organization")
public class DocumentTemplateController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DocumentTemplateController.class);

    private final DocumentTemplateService documentTemplateService;
    private final DocumentTemplateQueryService documentTemplateQueryService;
    private final LoggingService loggingService;

    /**
     * Lists one page of the organization's templates, sorted by name, then language, so the
     * translations of one template sit next to each other.
     *
     * Delegates to {@link DocumentTemplateQueryService#list}. The order is fixed, so sortBy
     * and sortDirection are ignored; the name search, the type, the language and the legal
     * entity are optional filters. The legal entity filter keeps the templates that legal
     * entity may use: its own and those without a legal entity. The items carry no content and no file URLs; the detail does.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}; unused here, the
     *                       access check already ran
     * @param organizationId the organization from the X-Organization-ID header
     * @param request        the one-based page, the page size and the optional search, type, language and
     *                       legal entity
     * @return 200 with the page of {@link DocumentTemplateListResponse} items and the paging data
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List document templates",
            description = "One page of the organization's templates by name, then language, with an optional "
                    + "search on name and optional type, language and legalEntityId filters. legalEntityId keeps "
                    + "the templates of that legal entity and those without one. The order is fixed; sortBy and "
                    + "sortDirection are ignored.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "422", description = "Invalid paging, search, type, language or legal entity "
                    + "id (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<DocumentTemplateListResponse>>> listDocumentTemplates(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject DocumentTemplateQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentTemplateControllerLogConstant.TEMPLATE_LIST_REQUEST);

        Page<DocumentTemplateListResponse> response = documentTemplateQueryService.list(organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentTemplateControllerLogConstant.TEMPLATE_LIST_COMPLETED);

        return ResponseUtil.ok(DocumentTemplateControllerResponseConstant.RESPONSE_TEMPLATE_LIST_RETRIEVED, response);
    }

    /**
     * Creates a template of the organization, without files.
     *
     * Delegates to {@link DocumentTemplateService#create}. The template is not ready until
     * {@link #replaceDocumentTemplateFiles} uploads its files. The name must be free in the
     * language within the organization, case ignored, and a given legal entity must be one of
     * the organization.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}, recorded as creator
     * @param organizationId the organization from the X-Organization-ID header, which owns the new template
     * @param request        the type, name, language and optional legal entity
     * @return 201 with the created {@link DocumentTemplateDetailResponse}
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#STA_DOCUMENT_TEMPLATE_NAME_TAKEN}
     *         (DOC_STA_001) when another template of the organization has the name in the language
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.shrine.legalentity.exception.ExceptionDomainLegalEntityError#NOT_LEGAL_ENTITY_FOUND}
     *         (LGE_NOT_001) when the legal entity is not one of the organization
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Create document template",
            description = "Creates a template of the organization, without files; it is not ready until its "
                    + "files are uploaded. legalEntityId is optional; null lets every legal entity of the "
                    + "organization use it. Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Legal entity not found or of another organization "
                    + "(LGE_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "Another template of the organization has this name in "
                    + "this language, case ignored (DOC_STA_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DocumentTemplateDetailResponse>> createDocumentTemplate(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid DocumentTemplateCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentTemplateControllerLogConstant.TEMPLATE_CREATE_REQUEST);

        DocumentTemplateDetailResponse response = documentTemplateService.create(organizationId, user, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentTemplateControllerLogConstant.TEMPLATE_CREATE_COMPLETED);

        return ResponseUtil.created(DocumentTemplateControllerResponseConstant.RESPONSE_TEMPLATE_CREATED, response);
    }

    /**
     * Reads every field of one template of the organization.
     *
     * Delegates to {@link DocumentTemplateQueryService#get}. The file URLs in the response
     * are short-lived, read-only signed URLs of the private blob container, so the client
     * opens them at once and asks again later rather than storing them.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}; unused here, the
     *                       access check already ran
     * @param organizationId the organization from the X-Organization-ID header
     * @param templateId     the template to read
     * @return 200 with the {@link DocumentTemplateDetailResponse}
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#NOT_DOCUMENT_TEMPLATE_FOUND}
     *         (DOC_NOT_001) when the organization has no template with the id
     */
    @GetMapping("/{templateId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Get document template",
            description = "Every field of one template of the organization, with short-lived signed read URLs "
                    + "of its files.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Template not found or of another organization "
                    + "(DOC_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DocumentTemplateDetailResponse>> getDocumentTemplate(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID templateId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentTemplateControllerLogConstant.TEMPLATE_GET_REQUEST);

        DocumentTemplateDetailResponse response = documentTemplateQueryService.get(organizationId, templateId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentTemplateControllerLogConstant.TEMPLATE_GET_COMPLETED);

        return ResponseUtil.ok(DocumentTemplateControllerResponseConstant.RESPONSE_TEMPLATE_RETRIEVED, response);
    }

    /**
     * Replaces the type, name, language and legal entity of a template.
     *
     * Delegates to {@link DocumentTemplateService#update}. A full replace, as every PUT of
     * this API, so a null legal entity clears it. The type may change, and the files stay;
     * they change only through {@link #replaceDocumentTemplateFiles}. Attachments already
     * copied from the template keep their own content.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}, recorded as last editor
     * @param organizationId the organization from the X-Organization-ID header
     * @param templateId     the template to update
     * @param request        the new type, name, language and legal entity
     * @return 200 with the updated {@link DocumentTemplateDetailResponse}
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#NOT_DOCUMENT_TEMPLATE_FOUND}
     *         (DOC_NOT_001) when the organization has no template with the id
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#STA_DOCUMENT_TEMPLATE_NAME_TAKEN}
     *         (DOC_STA_001) when another template of the organization has the name in the language
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.shrine.legalentity.exception.ExceptionDomainLegalEntityError#NOT_LEGAL_ENTITY_FOUND}
     *         (LGE_NOT_001) when the legal entity is not one of the organization
     */
    @PutMapping("/{templateId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Update document template",
            description = "Replaces the type, name, language and legal entity of a template; the files stay. A "
                    + "null legalEntityId clears it. Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Template not found or of another organization "
                    + "(DOC_NOT_001), or legal entity not found or of another organization (LGE_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "Another template of the organization has this name in "
                    + "this language, case ignored (DOC_STA_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DocumentTemplateDetailResponse>> updateDocumentTemplate(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID templateId,
            @RequestBody @Valid DocumentTemplateUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentTemplateControllerLogConstant.TEMPLATE_UPDATE_REQUEST);

        DocumentTemplateDetailResponse response = documentTemplateService.update(organizationId, user, templateId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentTemplateControllerLogConstant.TEMPLATE_UPDATE_COMPLETED);

        return ResponseUtil.ok(DocumentTemplateControllerResponseConstant.RESPONSE_TEMPLATE_UPDATED, response);
    }

    /**
     * Replaces both files of a template: the HTML source and the PDF rendered from it, sent together as the multipart parts named by
     * {@link DocumentTemplateConstant#PART_HTML} and {@link DocumentTemplateConstant#PART_PDF}.
     *
     * Delegates to {@link DocumentTemplateService#replaceFiles}, which uploads the new blobs,
     * points the template at them and only then deletes the old blobs, because Blob Storage
     * is outside the database transaction. Both parts are required, so the PDF always
     * matches its HTML. A missing part is refused by Spring before the method runs (400);
     * files above the multipart limit are refused before it too (413).
     *
     * @param user           the authenticated user, resolved by {@link GetUser}, recorded as last editor
     * @param organizationId the organization from the X-Organization-ID header
     * @param templateId     the template whose files are replaced
     * @param html           the HTML part, declared as text/html
     * @param pdf            the PDF part, declared as application/pdf
     * @return 200 with the updated {@link DocumentTemplateDetailResponse} and signed URLs of the new files
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#NOT_DOCUMENT_TEMPLATE_FOUND}
     *         (DOC_NOT_001) when the organization has no template with the id
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#VAL_FILE_NOT_HTML_OR_PDF}
     *         (DOC_VAL_001) when a part is empty or not an HTML or PDF file
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.IntegrationErrorException
     *         {@link com.korur.oba.connect.azure.exception.ExceptionDomainAzureError#UPS_AZURE_BLOB_UPLOAD}
     *         (AZR_UPS_001) when a file can not be uploaded
     */
    @PutMapping(value = "/{templateId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Replace document template files",
            description = "Multipart: part 'html' is the HTML file (Content-Type text/html), part 'pdf' the PDF "
                    + "rendered from it (Content-Type application/pdf); both are required. The old files are "
                    + "deleted. Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header, or a "
                    + "missing part"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Template not found or of another organization "
                    + "(DOC_NOT_001)"),
            @ApiResponse(responseCode = "413", description = "Files exceed the upload limit"),
            @ApiResponse(responseCode = "422", description = "A part is empty or not an HTML or PDF file "
                    + "(DOC_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error, among them a failed upload "
                    + "to the file store (AZR_UPS_001)")
    })
    public ResponseEntity<CustomResponse<DocumentTemplateDetailResponse>> replaceDocumentTemplateFiles(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID templateId,
            @RequestPart(DocumentFileConstant.PART_HTML) MultipartFile html,
            @RequestPart(DocumentFileConstant.PART_PDF) MultipartFile pdf) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentTemplateControllerLogConstant.TEMPLATE_FILES_REQUEST);

        DocumentTemplateDetailResponse response =
                documentTemplateService.replaceFiles(organizationId, user, templateId, html, pdf);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentTemplateControllerLogConstant.TEMPLATE_FILES_COMPLETED);

        return ResponseUtil.ok(DocumentTemplateControllerResponseConstant.RESPONSE_TEMPLATE_FILES_REPLACED, response);
    }

    /**
     * Deletes a template and its files.
     *
     * Delegates to {@link DocumentTemplateService#delete}. Attachments copied from the template
     * stay with their own content and blob.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}; unused here, the
     *                       access check already ran
     * @param organizationId the organization from the X-Organization-ID header
     * @param templateId     the template to delete
     * @return 200 with no data
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#NOT_DOCUMENT_TEMPLATE_FOUND}
     *         (DOC_NOT_001) when the organization has no template with the id
     */
    @DeleteMapping("/{templateId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete document template",
            description = "Removes a template and its files. Attachments copied from it stay. "
                    + "Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Template not found or of another organization "
                    + "(DOC_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteDocumentTemplate(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID templateId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentTemplateControllerLogConstant.TEMPLATE_DELETE_REQUEST);

        documentTemplateService.delete(organizationId, templateId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentTemplateControllerLogConstant.TEMPLATE_DELETE_COMPLETED);

        return ResponseUtil.ok(DocumentTemplateControllerResponseConstant.RESPONSE_TEMPLATE_DELETED);
    }
}
