package com.korur.oba.apps.kaya.version.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.FileMode;

/**
 * One rendered file of a version without its content, as the version detail lists it.
 */
@Schema(description = "A file of a version, without its content")
public record VersionFileResponse(
        @Schema(description = "Path relative to the repository root", example = ".agents/rules/core.md")
        String path,

        @Schema(description = "SHA-256 of the content, lower-case hex; null for a SYMLINK", nullable = true,
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        String sha256,

        @Schema(description = "FILE, EXECUTABLE or SYMLINK", example = "FILE")
        FileMode mode,

        @Schema(description = "Content size in bytes; null for a SYMLINK", nullable = true, example = "2048")
        Integer size,

        @Schema(description = "Relative target of a SYMLINK; null otherwise", nullable = true, example = "../.agents/skills/kaizen")
        String symlinkTarget,

        @Schema(description = "Path of the inherited file a project file replaces; null otherwise", nullable = true,
                example = ".agents/rules/16-kagi-api-first.md")
        String overridesPath
) {
}
