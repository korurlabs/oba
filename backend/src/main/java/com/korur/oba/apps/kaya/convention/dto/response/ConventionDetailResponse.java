package com.korur.oba.apps.kaya.convention.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * One convention with its index fields and the whole file as stored.
 */
@Schema(description = "A convention of a version in full, with the file as stored")
public record ConventionDetailResponse(
        @Schema(description = "Convention id", example = "8f2e3d4c-5b6a-4f7e-8d9c-2b3c4d5e6f7a")
        UUID id,

        @Schema(description = "Version the convention belongs to", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID versionId,

        @Schema(description = "Path below docs/private/conventions/; null when delivered only inside skills",
                nullable = true, example = "backend/logging.md")
        String path,

        @Schema(description = "Title from the frontmatter", nullable = true, example = "Logging convention")
        String title,

        @Schema(description = "Path of the pack convention this convention replaces", nullable = true,
                example = "backend/logging.md")
        String overridesPath,

        @Schema(description = "Byte length of the file", example = "4096")
        Integer sizeBytes,

        @Schema(description = "The whole file as UTF-8 text, frontmatter included",
                example = "---\ntitle: Logging convention\n---\n# Logging\n")
        String content,

        @Schema(description = "When the convention was stored", example = "2026-10-23T09:15:00Z")
        Instant createdAt,

        @Schema(description = "When the convention was last changed", example = "2026-10-23T09:15:00Z")
        Instant updatedAt
) {
}
