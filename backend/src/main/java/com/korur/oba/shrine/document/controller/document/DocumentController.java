package com.korur.oba.shrine.document.controller.document;

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
import com.korur.oba.shrine.document.constant.controller.document.DocumentControllerLogConstant;
import com.korur.oba.shrine.document.constant.controller.document.DocumentControllerResponseConstant;
import com.korur.oba.shrine.document.dto.request.document.DocumentAttachRequest;
import com.korur.oba.shrine.document.dto.request.document.DocumentCreateRequest;
import com.korur.oba.shrine.document.dto.request.document.DocumentQueryRequest;
import com.korur.oba.shrine.document.dto.request.document.DocumentUpdateRequest;
import com.korur.oba.shrine.document.dto.response.document.DocumentDetailResponse;
import com.korur.oba.shrine.document.dto.response.document.DocumentListResponse;
import com.korur.oba.shrine.document.service.document.DocumentQueryService;
import com.korur.oba.shrine.document.service.document.DocumentService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;

/**
 * REST controller under /documents for the living business documents of an organization:
 * proposals, NDAs, reports and contracts of an owner (a relation for now), each issued by a
 * legal entity and held as an HTML file and the PDF rendered from it. Documents are
 * organization-wide records used by apps, so they live in shrine and not under an app path.
 *
 * Access. Every endpoint needs {@link OrganizationMemberRole#MEMBER} through
 * {@link RequiredUserAccess}; the rest is access to the owner, which the services check
 * through {@link com.korur.oba.shrine.access.service.ResourceAccessQueryService}: READ to see
 * a document, WRITE to create or change one, DELETE to delete one. For a relation owner that
 * means Otak access. A document of another organization answers 404 like a missing one.
 *
 * Content. A document is created as JSON, empty or copied from a template, and receives new
 * files through {@link #replaceDocumentFiles}, typically from the oba CLI after a local edit.
 * {@link #attachDocument} stores the current PDF on a mail as a snapshot that later uploads
 * never change.
 *
 * Each method only logs, delegates to {@link DocumentService} for writes or
 * {@link DocumentQueryService} for reads, and wraps the result in a {@link CustomResponse}
 * through {@link ResponseUtil}. Errors are turned into the error response by
 * {@link com.korur.oba.dojo.exception.handler.mvc_layer.GlobalExceptionHandler}.
 *
 * Clients: the document pages of the Otak web app and the oba CLI.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/documents")
@Tag(name = "Documents", description = "Business documents of an organization's relations")
public class DocumentController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DocumentController.class);

    private final DocumentService documentService;
    private final DocumentQueryService documentQueryService;
    private final LoggingService loggingService;

    /**
     * Lists one page of the organization's documents, newest change first.
     *
     * Delegates to {@link DocumentQueryService#list}. The order is fixed, so sortBy and
     * sortDirection are ignored. The items carry no file URLs; the detail does.
     *
     * @param user           the authenticated user, resolved by {@link GetUser}
     * @param organizationId the organization from the X-Organization-ID header
     * @param request        the one-based page, the page size and the optional filters
     * @return 200 with the page of {@link DocumentListResponse} items and the paging data
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List documents",
            description = "One page of the organization's documents by last change, newest first, with optional "
                    + "relationId, legalEntityId, language, type and a search on title. The order is fixed; "
                    + "sortBy and sortDirection are ignored.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "No Otak access (APP_SEC_001), relation filter of "
                    + "another organization (APP_SEC_002), GUEST role (ORG_SEC_003), or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Relation filter not found (OTK_NOT_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid paging or filter (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<DocumentListResponse>>> listDocuments(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject DocumentQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DocumentControllerLogConstant.DOCUMENT_LIST_REQUEST);

        Page<DocumentListResponse> response = documentQueryService.list(user, organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DocumentControllerLogConstant.DOCUMENT_LIST_COMPLETED);

        return ResponseUtil.ok(DocumentControllerResponseConstant.RESPONSE_DOCUMENT_LIST_RETRIEVED, response);
    }

    /**
     * Creates a document of an owner, empty or with copies of a template's files.
     *
     * Delegates to {@link DocumentService#create}.
     *
     * @param user           the authenticated user, recorded as creator and last editor
     * @param organizationId the organization from the X-Organization-ID header
     * @param request        the owner, legal entity, fields and optional template
     * @return 201 with the created {@link DocumentDetailResponse}
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create document",
            description = "Creates a document of an owner (entityType RELATION). With templateId, the template's "
                    + "files are copied to new blobs; otherwise the document has no files until they are "
                    + "uploaded. Needs WRITE on the owner.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "No write access to the owner (APP_SEC_001, "
                    + "ORG_SEC_003), owner of another organization (APP_SEC_002), or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Owner not found (OTK_NOT_001), or legal entity "
                    + "(LGE_NOT_001) or template (DOC_NOT_001) not found or of another organization"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error, among them a failed copy in "
                    + "the file store (AZR_UPS_001, AZR_UPS_002)")
    })
    public ResponseEntity<CustomResponse<DocumentDetailResponse>> createDocument(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid DocumentCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DocumentControllerLogConstant.DOCUMENT_CREATE_REQUEST);

        DocumentDetailResponse response = documentService.create(organizationId, user, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentControllerLogConstant.DOCUMENT_CREATE_COMPLETED);

        return ResponseUtil.created(DocumentControllerResponseConstant.RESPONSE_DOCUMENT_CREATED, response);
    }

    /**
     * Reads one document with signed file URLs and its snapshots.
     *
     * Delegates to {@link DocumentQueryService#get}. The URLs are short-lived, so the client
     * opens them at once rather than storing them.
     *
     * @param user           the authenticated user
     * @param organizationId the organization from the X-Organization-ID header
     * @param documentId     the document
     * @return 200 with the {@link DocumentDetailResponse}
     */
    @GetMapping("/{documentId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Get document",
            description = "Every field of one document, short-lived signed read URLs of its files, and its "
                    + "snapshots: attachment id, mail id, outreach title and creation time, newest first. Needs "
                    + "READ on the owner.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "No read access to the owner (APP_SEC_001, "
                    + "ORG_SEC_003), or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Document not found or of another organization "
                    + "(DOC_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DocumentDetailResponse>> getDocument(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID documentId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DocumentControllerLogConstant.DOCUMENT_GET_REQUEST);

        DocumentDetailResponse response = documentQueryService.get(user, organizationId, documentId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DocumentControllerLogConstant.DOCUMENT_GET_COMPLETED);

        return ResponseUtil.ok(DocumentControllerResponseConstant.RESPONSE_DOCUMENT_RETRIEVED, response);
    }

    /**
     * Replaces the title, type, language and legal entity of a document; the files stay.
     *
     * Delegates to {@link DocumentService#update}.
     *
     * @param user           the authenticated user, recorded as last editor
     * @param organizationId the organization from the X-Organization-ID header
     * @param documentId     the document
     * @param request        the new fields
     * @return 200 with the updated {@link DocumentDetailResponse}
     */
    @PutMapping("/{documentId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update document",
            description = "Replaces the title, type, language and legal entity of a document; the files stay. "
                    + "Needs WRITE on the owner.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "No write access to the owner (APP_SEC_001, "
                    + "ORG_SEC_003), or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Document (DOC_NOT_002) or legal entity (LGE_NOT_001) "
                    + "not found or of another organization"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DocumentDetailResponse>> updateDocument(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID documentId,
            @RequestBody @Valid DocumentUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DocumentControllerLogConstant.DOCUMENT_UPDATE_REQUEST);

        DocumentDetailResponse response = documentService.update(organizationId, user, documentId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentControllerLogConstant.DOCUMENT_UPDATE_COMPLETED);

        return ResponseUtil.ok(DocumentControllerResponseConstant.RESPONSE_DOCUMENT_UPDATED, response);
    }

    /**
     * Replaces both files of a document, sent together as the multipart parts named by
     * {@link DocumentFileConstant#PART_HTML} and {@link DocumentFileConstant#PART_PDF}.
     *
     * Delegates to {@link DocumentService#replaceFiles}. Snapshots made earlier keep their own
     * copy.
     *
     * @param user           the authenticated user, recorded as last editor
     * @param organizationId the organization from the X-Organization-ID header
     * @param documentId     the document
     * @param html           the HTML part, declared as text/html
     * @param pdf            the PDF part, declared as application/pdf
     * @return 200 with the updated {@link DocumentDetailResponse} and signed URLs of the new files
     */
    @PutMapping(value = "/{documentId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Replace document files",
            description = "Multipart: part 'html' is the HTML file (Content-Type text/html), part 'pdf' the PDF "
                    + "rendered from it (Content-Type application/pdf); both are required. The old files are "
                    + "deleted; snapshots keep their own copy. Needs WRITE on the owner.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header, or a "
                    + "missing part"),
            @ApiResponse(responseCode = "403", description = "No write access to the owner (APP_SEC_001, "
                    + "ORG_SEC_003), or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Document not found or of another organization "
                    + "(DOC_NOT_002)"),
            @ApiResponse(responseCode = "413", description = "Files exceed the upload limit"),
            @ApiResponse(responseCode = "422", description = "A part is empty or not an HTML or PDF file "
                    + "(DOC_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error, among them a failed upload "
                    + "to the file store (AZR_UPS_001)")
    })
    public ResponseEntity<CustomResponse<DocumentDetailResponse>> replaceDocumentFiles(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID documentId,
            @RequestPart(DocumentFileConstant.PART_HTML) MultipartFile html,
            @RequestPart(DocumentFileConstant.PART_PDF) MultipartFile pdf) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DocumentControllerLogConstant.DOCUMENT_FILES_REQUEST);

        DocumentDetailResponse response = documentService.replaceFiles(organizationId, user, documentId, html, pdf);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DocumentControllerLogConstant.DOCUMENT_FILES_COMPLETED);

        return ResponseUtil.ok(DocumentControllerResponseConstant.RESPONSE_DOCUMENT_FILES_REPLACED, response);
    }

    /**
     * Deletes a document and its files; its snapshots stay on their mails.
     *
     * Delegates to {@link DocumentService#delete}.
     *
     * @param user           the authenticated user
     * @param organizationId the organization from the X-Organization-ID header
     * @param documentId     the document
     * @return 200 with no data
     */
    @DeleteMapping("/{documentId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete document",
            description = "Removes a document and its files. Its snapshots stay on their mails, with documentId "
                    + "null. Needs DELETE on the owner.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "No delete access to the owner (APP_SEC_001, "
                    + "ORG_SEC_003), or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Document not found or of another organization "
                    + "(DOC_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteDocument(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID documentId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DocumentControllerLogConstant.DOCUMENT_DELETE_REQUEST);

        documentService.delete(organizationId, user, documentId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentControllerLogConstant.DOCUMENT_DELETE_COMPLETED);

        return ResponseUtil.ok(DocumentControllerResponseConstant.RESPONSE_DOCUMENT_DELETED);
    }

    /**
     * Stores the document's current PDF on a mail of the same relation as a snapshot.
     *
     * Delegates to {@link DocumentService#attachTo}.
     *
     * @param user           the authenticated user
     * @param organizationId the organization from the X-Organization-ID header
     * @param documentId     the document
     * @param request        the parent, a mail
     * @return 201 with the new {@link AttachmentResponse}, its documentId set
     */
    @PostMapping("/{documentId}/attachments")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Attach document to a mail",
            description = "Copies the document's current PDF to a new blob and stores it as an attachment of the "
                    + "mail (entityType MAIL) with documentId set. The mail must belong to the document's "
                    + "relation and must not be sent. Needs READ on the document's owner and WRITE on the mail "
                    + "(Otak admin).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "No read access to the owner or write access to the "
                    + "mail (APP_SEC_001, ORG_SEC_003)"),
            @ApiResponse(responseCode = "404", description = "Document (DOC_NOT_002) or mail (OTK_NOT_011) not "
                    + "found or of another organization"),
            @ApiResponse(responseCode = "409", description = "The mail's outreach is sent (OTK_STA_011)"),
            @ApiResponse(responseCode = "422", description = "Not a mail (GEN_VAL_001), no PDF (DOC_VAL_003), a "
                    + "mail of another relation (DOC_VAL_004), or the mail already holds this PDF (STG_VAL_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error, among them a failed copy in "
                    + "the file store (AZR_UPS_001, AZR_UPS_002)")
    })
    public ResponseEntity<CustomResponse<AttachmentResponse>> attachDocument(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID documentId,
            @RequestBody @Valid DocumentAttachRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, DocumentControllerLogConstant.DOCUMENT_ATTACH_REQUEST);

        AttachmentResponse response = documentService.attachTo(user, organizationId, documentId, request.entityType(),
                request.entityId());

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DocumentControllerLogConstant.DOCUMENT_ATTACH_COMPLETED);

        return ResponseUtil.created(DocumentControllerResponseConstant.RESPONSE_DOCUMENT_ATTACHED, response);
    }
}
