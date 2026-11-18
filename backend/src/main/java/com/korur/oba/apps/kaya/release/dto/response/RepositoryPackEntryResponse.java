package com.korur.oba.apps.kaya.release.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * One pack a repository composes, with the folder it is mounted in.
 */
@Schema(description = "A pack a repository composes, at the root or mounted in a folder")
public record RepositoryPackEntryResponse(
        @Schema(description = "Pack id", example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
        UUID id,

        @Schema(description = "Name, unique among the organization's packs", example = "kagi")
        String name,

        @Schema(description = "The parent pack; null for a root pack", nullable = true,
                example = "1b2c3d4e-5f60-4718-9a0b-1c2d3e4f5a6b")
        UUID parentPackId,

        @Schema(description = "The approved version the pack delivers; null until a first version is approved",
                nullable = true, example = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b")
        UUID currentVersionId,

        @Schema(description = "Whether every repository of the organization composes the pack", example = "false")
        boolean required,

        @Schema(description = "The mount folder; null for a pack composed at the repository root", nullable = true,
                example = "projects/kagi")
        String rootPath,

        @Schema(description = "When the pack was created", example = "2026-10-23T09:15:00Z")
        Instant createdAt,

        @Schema(description = "When the pack was last changed", example = "2026-10-23T09:15:00Z")
        Instant updatedAt
) {
}
