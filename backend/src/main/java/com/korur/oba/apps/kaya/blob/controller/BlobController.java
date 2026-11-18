package com.korur.oba.apps.kaya.blob.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.io.InputStream;
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
import com.korur.oba.apps.kaya.blob.constant.BlobConstant;
import com.korur.oba.apps.kaya.blob.constant.controller.BlobControllerLogConstant;
import com.korur.oba.apps.kaya.blob.constant.controller.BlobControllerResponseConstant;
import com.korur.oba.apps.kaya.blob.dto.response.BlobUploadResponse;
import com.korur.oba.apps.kaya.blob.service.BlobService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * File content of the organization's packs and projects, addressed by its SHA-256, stored through
 * {@link BlobService}. The seed uploads every
 * file it names here first; the server hashes the bytes again and stores each content once.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/blobs")
@Tag(name = "Blobs", description = "File content of packs and projects, stored under its SHA-256")
public class BlobController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BlobController.class);

    private final BlobService blobService;
    private final LoggingService loggingService;

    /**
     * Stores the request body under its SHA-256.
     */
    @PutMapping("/{sha256}")
    @RequiredUserAccess(OrganizationMemberRole.OWNER)
    @Operation(summary = "Upload file content",
            description = "The body is the raw bytes, at most 5 MiB, any content type (application/octet-stream is usual). "
                    + "The server hashes them again; content the organization already has is not stored twice",
            requestBody = @RequestBody(description = "The raw file bytes", required = true))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stored, or already stored (stored false)"),
            @ApiResponse(responseCode = "400", description = "KAY_CFG_005: the bytes do not hash to sha256; or X-Organization-ID "
                    + "missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not an OWNER of the organization"),
            @ApiResponse(responseCode = "413", description = "KAY_VAL_012: the body is over 5 MiB"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: sha256 is not 64 lower-case hex characters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<BlobUploadResponse>> uploadBlob(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable @Pattern(regexp = BlobConstant.SHA256_PATTERN, message = BlobConstant.SHA256_INVALID_MESSAGE)
            String sha256,
            @Parameter(hidden = true) InputStream body) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, BlobControllerLogConstant.BLOB_UPLOAD_REQUEST,
                sha256, organizationId);
        BlobUploadResponse response = blobService.uploadBlob(user, organizationId, sha256, body);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, BlobControllerLogConstant.BLOB_UPLOAD_COMPLETED,
                sha256, organizationId);
        return ResponseUtil.ok(BlobControllerResponseConstant.RESPONSE_BLOB_STORED, response);
    }
}
