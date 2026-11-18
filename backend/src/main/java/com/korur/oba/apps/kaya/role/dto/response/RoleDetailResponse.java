package com.korur.oba.apps.kaya.role.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * One role with its index fields and the whole file as stored.
 */
@Schema(description = "A role of a version in full, with the file as stored")
public record RoleDetailResponse(
        @Schema(description = "Role id", example = "8f2e3d4c-5b6a-4f7e-8d9c-2b3c4d5e6f7a")
        UUID id,

        @Schema(description = "Version the role belongs to", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID versionId,

        @Schema(description = "Path below .agents/roles/", example = "coding-engineer.md")
        String path,

        @Schema(description = "Title from the frontmatter", nullable = true, example = "Coding engineer")
        String title,

        @Schema(description = "Byte length of the file", example = "4096")
        Integer sizeBytes,

        @Schema(description = "The whole file as UTF-8 text, frontmatter included",
                example = "---\ntitle: Coding engineer\n---\n# Coding engineer\n")
        String content,

        @Schema(description = "When the role was stored", example = "2026-10-23T09:15:00Z")
        Instant createdAt,

        @Schema(description = "When the role was last changed", example = "2026-10-23T09:15:00Z")
        Instant updatedAt
) {
}
