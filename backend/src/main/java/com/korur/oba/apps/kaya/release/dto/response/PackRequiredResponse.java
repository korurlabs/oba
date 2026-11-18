package com.korur.oba.apps.kaya.release.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The required flag of a pack after a change, with the number of releases the change created.
 */
@Schema(description = "The required flag of a pack and the releases its change created")
public record PackRequiredResponse(
        @Schema(description = "The pack", example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
        UUID packId,

        @Schema(description = "Whether every repository of the organization now composes the pack", example = "true")
        boolean required,

        @Schema(description = "Releases created, one per active repository whose composition changed", example = "3")
        int releaseCount
) {
}
