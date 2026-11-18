package com.korur.oba.apps.kaya.release.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.release.enums.OverrideStatus;

/**
 * An own item's standing as an override: the ancestor version it was reviewed against, the version
 * the nearest ancestor delivers now, and the derived item numbers of both for the "updated to v5,
 * you override v4" text.
 */
@Schema(description = "How an own item overrides the item an ancestor delivers")
public record EffectiveOverrideResponse(
        @Schema(description = "The ancestor pack the override is based on", example = "1b2c3d4e-5f60-4718-9a0b-1c2d3e4f5a6b")
        UUID basePackId,

        @Schema(description = "The ancestor version the override was reviewed against; null when none is known",
                nullable = true, example = "4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a")
        UUID baseVersionId,

        @Schema(description = "The item's version number at the base version; null without a base", nullable = true,
                example = "4")
        Integer baseItemVersion,

        @Schema(description = "The current version of the nearest ancestor delivering the item; null when none does",
                nullable = true, example = "6f7a8b9c-0d1e-4f2a-8b3c-4d5e6f7a8b9c")
        UUID parentVersionId,

        @Schema(description = "The item's version number at the parent version; null when no ancestor delivers it",
                nullable = true, example = "5")
        Integer parentItemVersion,

        @Schema(description = "CURRENT, PARENT_UPDATED or PARENT_REMOVED", example = "PARENT_UPDATED")
        OverrideStatus status
) {
}
