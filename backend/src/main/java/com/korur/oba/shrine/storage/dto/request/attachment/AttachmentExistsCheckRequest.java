package com.korur.oba.shrine.storage.dto.request.attachment;

// 1. Framework-level
import jakarta.validation.constraints.NotNull;

// 4. Internal
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

/**
 * Request payload for the attachment exists-check endpoint.
 *
 * Carries the parent-entity type plus the original file name and is sent
 * alongside a multipart upload of the file content. The backend computes
 * a SHA-256 hash from the bytes and reports whether an attachment with
 * identical content already exists under the given entityType.
 *
 * Implements AttachmentMetadata so it can flow through the same
 * validator entry points as the upload request without duplicate code
 * paths.
 */
public record AttachmentExistsCheckRequest(
        @NotNull String fileName,
        @NotNull StorageEntityType entityType) implements AttachmentMetadata {
}
