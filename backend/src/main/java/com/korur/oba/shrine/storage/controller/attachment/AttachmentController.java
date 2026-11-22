package com.korur.oba.shrine.storage.controller.attachment;

// 1. Framework-level
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

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
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.storage.constant.controller.attachment.AttachmentControllerLogConstant;
import com.korur.oba.shrine.storage.constant.controller.attachment.AttachmentControllerResponseConstant;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentCreateRequest;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentExistsCheckRequest;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentExistsResponse;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

/**
 * REST entry point for the storage-attachment domain.
 *
 * The controller is intentionally thin: every endpoint logs around a
 * delegation to either AttachmentService (commands) or
 * AttachmentQueryService (queries) and wraps the outcome with
 * ResponseUtil. Validation and business invariants live in
 * AttachmentValidator and the services themselves so they stay covered
 * by unit tests rather than depending on a Spring-boot test harness.
 *
 * Every endpoint needs the X-Organization-ID header and at least GUEST in
 * that organization. The parent entity must belong to the same organization,
 * and the services check the role and rules of the action on it.
 */
@RestController
@RequestMapping("/storage/attachments")
@RequiredArgsConstructor
@Tag(name = "Attachments", description = "Attachment management")
public class AttachmentController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AttachmentController.class);

    private final AttachmentService attachmentService;
    private final AttachmentQueryService attachmentQueryService;
    private final LoggingService loggingService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Upload attachments")
    public ResponseEntity<CustomResponse<List<AttachmentResponse>>> uploadAttachments(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @RequestPart("metadata") List<AttachmentCreateRequest> metadata,
            @RequestPart("files") List<MultipartFile> files) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AttachmentControllerLogConstant.ATTACHMENT_UPLOAD_REQUEST);

        List<AttachmentResponse> response = attachmentService.uploadAttachments(user, organizationId, metadata,
                files);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AttachmentControllerLogConstant.ATTACHMENT_UPLOAD_REQUEST_COMPLETED);

        return ResponseUtil.created(AttachmentControllerResponseConstant.RESPONSE_ATTACHMENTS_UPLOADED, response);
    }

    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List attachments by entity")
    public ResponseEntity<CustomResponse<List<AttachmentResponse>>> listAttachments(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestParam StorageEntityType entityType,
            @RequestParam UUID entityId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AttachmentControllerLogConstant.ATTACHMENT_LIST_REQUEST);

        List<AttachmentResponse> response = attachmentQueryService.listAttachmentsByEntity(user, organizationId,
                entityType, entityId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AttachmentControllerLogConstant.ATTACHMENT_LIST_REQUEST_COMPLETED);

        return ResponseUtil.ok(AttachmentControllerResponseConstant.RESPONSE_ATTACHMENTS_RETRIEVED, response);
    }

    /**
     * Pre-flight duplicate check for a candidate upload.
     *
     * Lets the frontend ask "would this file collide with an existing
     * attachment under this parent?" before kicking off a real upload, so
     * the user can be warned without paying the cost of sending the bytes
     * twice. Uses POST despite being read-only because the candidate file
     * is sent in the request body — GET cannot carry a multipart payload.
     *
     * Rate-limited at the READ tier because the underlying path performs
     * no DB writes; the WRITE tier is reserved for the actual upload
     * endpoint.
     */
    @PostMapping(path = "/exists", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Check whether an attachment with identical content already exists")
    public ResponseEntity<CustomResponse<AttachmentExistsResponse>> checkAttachmentExists(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @RequestPart("metadata") AttachmentExistsCheckRequest metadata,
            @RequestPart("file") MultipartFile file) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AttachmentControllerLogConstant.ATTACHMENT_EXISTS_CHECK_REQUEST,
                metadata.entityType());

        AttachmentExistsResponse response = attachmentQueryService.checkAttachmentExists(organizationId, metadata,
                file);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AttachmentControllerLogConstant.ATTACHMENT_EXISTS_CHECK_REQUEST_COMPLETED, response.exists());

        return ResponseUtil.ok(AttachmentControllerResponseConstant.RESPONSE_ATTACHMENT_EXISTS_CHECKED, response);
    }

    @DeleteMapping("/{attachmentId}")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Delete attachment")
    public ResponseEntity<CustomResponse<Void>> deleteAttachment(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID attachmentId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AttachmentControllerLogConstant.ATTACHMENT_DELETE_REQUEST, attachmentId);

        attachmentService.deleteAttachment(user, organizationId, attachmentId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                AttachmentControllerLogConstant.ATTACHMENT_DELETE_REQUEST_COMPLETED, attachmentId);

        return ResponseUtil.ok(AttachmentControllerResponseConstant.RESPONSE_ATTACHMENT_DELETED);
    }
}
