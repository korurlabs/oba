package com.korur.oba.apps.kaya.release.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.release.enums.ItemLock;
import com.korur.oba.apps.kaya.render.enums.EntityKind;

/**
 * One item of a pack's effective config: an item the pack stores itself, or an item of an ancestor
 * that wins on the pack's chain.
 */
@Schema(description = "An item of a pack's effective config with the pack it comes from")
public record EffectiveItemResponse(
        @Schema(description = "The item: a file path, or a skill directory ending in a slash",
                example = ".agents/rules/10-backend.md")
        String path,

        @Schema(description = "What the item is stored as; null for a link no row stores", nullable = true,
                example = "RULE")
        EntityKind kind,

        @Schema(description = "The pack that gives the item", example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
        UUID sourcePackId,

        @Schema(description = "The version of the source pack that gives it", example = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b")
        UUID sourceVersionId,

        @Schema(description = "Whether the pack stores the item itself", example = "true")
        boolean own,

        @Schema(description = "Why the pack may not override the item; null when it may", nullable = true,
                example = "GUARDRAIL")
        ItemLock locked,

        @Schema(description = "The override an own item makes; null for an inherited item or an item no ancestor "
                + "gives", nullable = true)
        EffectiveOverrideResponse override
) {
}
