package com.korur.oba.apps.kaya.pack.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;

/**
 * One repository that composes a pack, with the folder it mounts the pack in and its latest release.
 */
@Schema(description = "A repository that composes the pack")
public record PackRepositoryResponse(
        @Schema(description = "Repository id", example = "3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b")
        UUID id,

        @Schema(description = "Display name", example = "village")
        String name,

        @Schema(description = "Normalised git remote: host/owner/name in lower case", example = "github.com/korurlabs/village")
        String remoteUrl,

        @Schema(description = "Repository status", example = "ACTIVE")
        RepositoryStatus status,

        @Schema(description = "The folder the repository mounts the pack in; null for a pack composed at the repository root",
                nullable = true, example = "projects/kagi")
        String rootPath,

        @Schema(description = "Number of the repository's latest release; null while it has none", nullable = true,
                example = "12")
        Integer latestReleaseNumber
) {
}
