package com.korur.oba.apps.kaya.version.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * An override a version records: its item replaces the item of an ancestor pack, based on that
 * ancestor's version.
 */
@Schema(description = "An override of an inherited item recorded in a version")
public record ItemOverrideResponse(
        @Schema(description = "The version that records the override", example = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b")
        UUID versionId,

        @Schema(description = "The item: a file path, or a skill directory ending in a slash",
                example = ".agents/rules/10-backend.md")
        String path,

        @Schema(description = "The ancestor pack whose item is overridden", example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
        UUID basePackId,

        @Schema(description = "The ancestor version the override was reviewed against; null when none is known",
                nullable = true, example = "4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a")
        UUID baseVersionId,

        @Schema(description = "When the override was recorded", example = "2026-10-28T09:15:00Z")
        Instant createdAt,

        @Schema(description = "When the base last changed", example = "2026-10-28T09:20:00Z")
        Instant updatedAt
) {
}
