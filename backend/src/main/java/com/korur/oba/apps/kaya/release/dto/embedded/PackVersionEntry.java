package com.korur.oba.apps.kaya.release.dto.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * One composed pack of a {@link com.korur.oba.apps.kaya.release.entity.Release}, in composition
 * order, as stored in its pack_versions column. It records which version of each pack, ancestors
 * included, a release was built from; it is not part of the signed document, so a release signed
 * before the column existed keeps a valid signature.
 */
@Schema(description = "A pack a release composes, with its version and mount folder")
public record PackVersionEntry(
        @Schema(description = "The pack", example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
        UUID packId,

        @Schema(description = "The composed version; null while the pack has no approved version", nullable = true,
                example = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b")
        UUID versionId,

        @Schema(description = "The mount folder; null for a pack composed at the repository root", nullable = true,
                example = "projects/kagi")
        String rootPath
) {
}
