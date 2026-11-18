package com.korur.oba.apps.kaya.seed.dto.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * A signed repository release a seed run created. The CLI reads its files from
 * GET /repositories/{repositoryId}/releases/{number} to compare them with the repository.
 */
@Schema(description = "A repository release a seed run created")
public record SeedReleaseReport(
        @Schema(description = "The repository", example = "3c4d5e6f-7a8b-4c9d-0e1f-2a3b4c5d6e7f")
        UUID repositoryId,

        @Schema(description = "Release number, growing per repository", example = "3")
        int number,

        @Schema(description = "SHA-256 of the signed document", example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        String manifestSha256,

        @Schema(description = "Delivered paths of the release", example = "212")
        int fileCount
) {
}
