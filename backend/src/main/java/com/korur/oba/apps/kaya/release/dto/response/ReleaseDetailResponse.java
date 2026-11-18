package com.korur.oba.apps.kaya.release.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.release.dto.embedded.PackVersionEntry;
import com.korur.oba.apps.kaya.release.dto.embedded.ReleaseEntry;

/**
 * One signed release of a repository with its signature and the paths it delivers.
 */
@Schema(description = "A signed release of a repository with its signature and delivered paths")
public record ReleaseDetailResponse(
        @Schema(description = "Release id", example = "3e4f5a6b-7c8d-4e9f-0a1b-2c3d4e5f6a7b")
        UUID id,

        @Schema(description = "The repository", example = "4f5a6b7c-8d9e-4f0a-1b2c-3d4e5f6a7b8c")
        UUID repositoryId,

        @Schema(description = "Release number, growing per repository from 1", example = "7")
        Integer number,

        @Schema(description = "The approved version that led to the release", example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
        UUID causeVersionId,

        @Schema(description = "SHA-256 of the signed document",
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        String manifestSha256,

        @Schema(description = "Base64 Ed25519 signature of the signed document")
        String signature,

        @Schema(description = "Id of the signing key", example = "kaya-2026-10")
        String keyId,

        @Schema(description = "Number of delivered paths", example = "42")
        int fileCount,

        @Schema(description = "When the release was created, as signed")
        Instant createdAt,

        @Schema(description = "The delivered paths, by path code point by code point")
        List<ReleaseEntry> files,

        @Schema(description = "Every composed pack, ancestors included, with its version and mount folder, in composition "
                + "order; not signed, and empty for a release made before packs had parents")
        List<PackVersionEntry> packVersions
) {
}
