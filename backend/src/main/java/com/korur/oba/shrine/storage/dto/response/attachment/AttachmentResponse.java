package com.korur.oba.shrine.storage.dto.response.attachment;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

/**
 * An attachment as clients see it.
 *
 * width and height are the image's pixel size, both set for a PNG, JPEG, GIF
 * or WebP upload whose header could be read and both null otherwise, so a
 * client lays out an image without them in a fallback box until it loads.
 *
 * documentId names the document an attachment is a PDF snapshot of; it is null for an
 * uploaded file and becomes null when that document is deleted.
 */
public record AttachmentResponse(
        UUID id,
        String url,
        String fileName,
        String contentType,
        Long fileSize,
        StorageEntityType entityType,
        UUID entityId,
        Instant createdAt,
        Integer width,
        Integer height,
        UUID documentId) {
}
