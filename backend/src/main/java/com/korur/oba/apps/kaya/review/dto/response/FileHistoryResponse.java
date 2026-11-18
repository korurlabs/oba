package com.korur.oba.apps.kaya.review.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.version.enums.FileChange;
import com.korur.oba.apps.kaya.version.enums.VersionSource;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;

/**
 * One version in the history of a file, with who proposed it and who reviewed it, for the version
 * menu and the history panel of a document.
 */
@Schema(description = "A version that added, changed or deleted the file, with its reviews")
public record FileHistoryResponse(
        @Schema(description = "Version id", example = "6b0e2f4a-1c3d-4e5f-8a9b-0c1d2e3f4a5b")
        UUID versionId,

        @Schema(description = "Version number within its pack or project", example = "7")
        Integer number,

        @Schema(description = "Status: APPROVED, SUPERSEDED, IN_REVIEW, or DRAFT for the caller's own draft", example = "APPROVED")
        VersionStatus status,

        @Schema(description = "Where the version came from", example = "MANUAL")
        VersionSource source,

        @Schema(description = "How the version changed the file against its base: ADDED, MODIFIED or DELETED", example = "MODIFIED")
        FileChange change,

        @Schema(description = "SHA-256 of the file in this version; null when DELETED", nullable = true,
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        String sha256,

        @Schema(description = "Whether this is the current version of the pack or project", example = "true")
        Boolean current,

        @Schema(description = "User who proposed the version", example = "4e5f6a7b-8c9d-4e0f-a1b2-c3d4e5f6a7b8")
        UUID proposedBy,

        @Schema(description = "Reason given on submit; null for a draft never submitted", nullable = true,
                example = "Allow the kaizen CLI in every repository")
        String reason,

        @Schema(description = "When the version was submitted for review", nullable = true)
        Instant submittedAt,

        @Schema(description = "When the version was approved or rejected", nullable = true)
        Instant decidedAt,

        @Schema(description = "Reviews, oldest first")
        List<ReviewResponse> reviews
) {
}
