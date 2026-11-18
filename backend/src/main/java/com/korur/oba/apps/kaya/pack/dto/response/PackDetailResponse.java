package com.korur.oba.apps.kaya.pack.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * A pack as the pack page shows it: the fields of {@link PackResponse} and the repositories that
 * compose the pack.
 */
@Schema(description = "A pack of shared agent config of the organization, with the repositories that compose it")
public record PackDetailResponse(
        @Schema(description = "Pack id", example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
        UUID id,

        @Schema(description = "Name, unique among the organization's packs", example = "family-java")
        String name,

        @Schema(description = "What the pack is for, plain text; null for none", nullable = true,
                example = "Java conventions shared by every backend repository")
        String description,

        @Schema(description = "The parent pack; null for a root pack", nullable = true,
                example = "1b2c3d4e-5f60-4718-9a0b-1c2d3e4f5a6b")
        UUID parentPackId,

        @Schema(description = "The approved version the pack delivers; null until a first version is approved",
                nullable = true, example = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b")
        UUID currentVersionId,

        @Schema(description = "Whether every repository of the organization composes the pack, ahead of its own packs",
                example = "false")
        boolean required,

        @Schema(description = "The distinct folders the pack is mounted in, in any repository of the organization; empty "
                + "for a pack only composed at repository roots", example = "[\"projects/kagi\"]")
        List<String> mountPaths,

        @Schema(description = "When the pack was created", example = "2026-10-23T09:15:00Z")
        Instant createdAt,

        @Schema(description = "When the pack was last changed", example = "2026-10-23T09:15:00Z")
        Instant updatedAt,

        @Schema(description = "The repositories of the organization that compose the pack, by name: those that list it, "
                + "or every active repository when the pack is required")
        List<PackRepositoryResponse> repositories
) {
}
