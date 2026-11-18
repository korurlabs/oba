package com.korur.oba.apps.kaya.version.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.EntityKind;

/**
 * What a draft file save stored, as
 * {@link com.korur.oba.apps.kaya.version.service.VersionService#saveFile} answers it. The stored file
 * is the content as sent, so the response names the entity and its hash instead of echoing the bytes;
 * the editor opens the entity by its kind and id.
 */
@Schema(description = "The file a draft save stored, without its content")
public record DraftFileSaveResponse(
        @Schema(description = "Path relative to the repository root", example = ".agents/rules/core.md")
        String path,

        @Schema(description = "Kind of the entity the file is stored as; a file of a skill directory is the skill for "
                + "SKILL.md and otherwise its part", example = "RULE")
        EntityKind kind,

        @Schema(description = "Id of the entity of that kind", example = "3f2b8c1e-9d4a-4e6b-8a7c-1d2e3f4a5b6c")
        UUID entityId,

        @Schema(description = "SHA-256 of the stored content, lower-case hex",
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        String sha256,

        @Schema(description = "Content size in bytes", example = "2048")
        Integer sizeBytes,

        @Schema(description = "Whether the file is stored with the executable bit", example = "false")
        boolean executable,

        @Schema(description = "Path of the inherited file a project file replaces; null otherwise", nullable = true,
                example = ".agents/rules/16-kagi-api-first.md")
        String overridesPath
) {
}
