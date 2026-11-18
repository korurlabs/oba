package com.korur.oba.apps.kaya.version.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.version.enums.VersionSource;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;

/**
 * A {@link com.korur.oba.apps.kaya.version.entity.Version} with the file list its stored files render,
 * each a {@link VersionFileResponse}.
 */
@Schema(description = "A version of a pack with its rendered file list")
public record VersionDetailResponse(
        @Schema(description = "Version id", example = "6b0e2f4a-1c3d-4e5f-8a9b-0c1d2e3f4a5b")
        UUID id,

        @Schema(description = "Pack the version belongs to", example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
        UUID packId,

        @Schema(description = "Version number within its pack, from 1", example = "7")
        Integer number,

        @Schema(description = "Status", example = "APPROVED")
        VersionStatus status,

        @Schema(description = "Version this one was derived from; null for a first version", nullable = true)
        UUID baseVersionId,

        @Schema(description = "User who proposed the version", example = "4e5f6a7b-8c9d-4e0f-a1b2-c3d4e5f6a7b8")
        UUID proposedBy,

        @Schema(description = "Where the version came from", example = "MANUAL")
        VersionSource source,

        @Schema(description = "Reason given on submit", nullable = true, example = "Allow the oba CLI in every repository")
        String reason,

        @Schema(description = "Whether the change touches hooks, permissions or executable files", example = "false")
        Boolean executableChange,

        @Schema(description = "SHA-256 of the version's manifest; null for a draft", nullable = true)
        String manifestSha256,

        @Schema(description = "When the version was submitted for review", nullable = true)
        Instant submittedAt,

        @Schema(description = "When the version was approved or rejected", nullable = true)
        Instant decidedAt,

        @Schema(description = "When the version was created")
        Instant createdAt,

        @Schema(description = "When the version was last changed")
        Instant updatedAt,

        @Schema(description = "Rendered files of the version, by path")
        List<VersionFileResponse> files
) {
}
