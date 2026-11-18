package com.korur.oba.apps.kaya.blob.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

/**
 * The outcome of a PUT /blobs/{sha256} upload.
 */
@Schema(description = "Content stored under its SHA-256")
public record BlobUploadResponse(
        @Schema(description = "SHA-256 of the content, as re-computed by the server",
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        String sha256,

        @Schema(description = "Bytes of the content", example = "2048")
        int size,

        @Schema(description = "True when this upload stored the content, false when the organization already had it",
                example = "true")
        boolean stored
) {
}
