package com.korur.oba.apps.kaya.reference.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * One reference with its index fields and the whole file as stored, as text or, for a binary file, as
 * base64. Exactly one of content and contentBase64 is set.
 */
@Schema(description = "A reference file of a version in full, with the file as stored")
public record ReferenceDetailResponse(
        @Schema(description = "Reference id", example = "4d3c2b1a-0f9e-4d8c-b7a6-5f4e3d2c1b0a")
        UUID id,

        @Schema(description = "Version the reference belongs to", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID versionId,

        @Schema(description = "File name", example = "api-routes.md")
        String name,

        @Schema(description = "Media type of the file", example = "text/markdown")
        String mediaType,

        @Schema(description = "Byte length of the file", example = "2048")
        Integer sizeBytes,

        @Schema(description = "The whole file as UTF-8 text; null when the file is not valid UTF-8", nullable = true,
                example = "# API routes\n")
        String content,

        @Schema(description = "The whole file as base64; set only when the file is not valid UTF-8", nullable = true,
                example = "iVBORw0KGgo=")
        String contentBase64,

        @Schema(description = "When the reference was stored", example = "2026-10-23T09:15:00Z")
        Instant createdAt,

        @Schema(description = "When the reference was last changed", example = "2026-10-23T09:15:00Z")
        Instant updatedAt
) {
}
