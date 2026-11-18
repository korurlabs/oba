package com.korur.oba.apps.kaya.release.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * The packs a repository composes, the organization's required packs first and then its own in
 * display order, each with its mount folder, and its latest release.
 */
@Schema(description = "The packs a repository composes and its latest release")
public record RepositoryPackResponse(
        @Schema(description = "The repository", example = "4f5a6b7c-8d9e-4f0a-1b2c-3d4e5f6a7b8c")
        UUID repositoryId,

        @Schema(description = "The required packs, then the repository's own packs in display order")
        List<RepositoryPackEntryResponse> packs,

        @Schema(description = "The latest release; null while nothing composed has an approved version", nullable = true)
        ReleaseResponse release
) {
}
