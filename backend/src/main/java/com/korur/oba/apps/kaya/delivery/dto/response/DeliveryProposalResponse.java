package com.korur.oba.apps.kaya.delivery.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.version.enums.VersionStatus;

/**
 * A proposal from a checkout in review, with the page where reviewers decide on it.
 */
@Schema(description = "A proposal in review, with the page where reviewers decide on it")
public record DeliveryProposalResponse(
        @Schema(description = "The proposed version", example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
        UUID proposalId,

        @Schema(description = "Version number within its pack", example = "12")
        Integer number,

        @Schema(description = "IN_REVIEW", example = "IN_REVIEW")
        VersionStatus status,

        @Schema(description = "The pack the proposal changes", example = "1d2e3f4a-5b6c-4d7e-8f9a-0b1c2d3e4f5a")
        UUID packId,

        @Schema(description = "Name of the pack", example = "village")
        String name,

        @Schema(description = "The version the proposal is based on; null for a pack without versions",
                nullable = true, example = "3d4e5f6a-7b8c-4d9e-0f1a-2b3c4d5e6f7a")
        UUID baseVersionId,

        @Schema(description = "The release the files were edited on", example = "7")
        Integer releaseNumber,

        @Schema(description = "Whether the change touches something that runs on a machine; only an OWNER approves it",
                example = "false")
        Boolean executableChange,

        @Schema(description = "The review page of the proposal in the Kaya web app",
                example = "https://kaya.korur.com/changes/2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
        String reviewUrl
) {
}
