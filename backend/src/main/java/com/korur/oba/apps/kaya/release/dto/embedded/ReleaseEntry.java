package com.korur.oba.apps.kaya.release.dto.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.FileMode;

/**
 * One delivered path of a {@link com.korur.oba.apps.kaya.release.entity.Release}, as stored in its
 * manifest column. The signed document carries path, sha256, mode, target, alwaysOn and deny; size, the
 * owner and its version, the override marker and the narrowing project stay on the server for the
 * size shown beside each rule and the origin of each file.
 */
@Schema(description = "A delivered path of a release")
public record ReleaseEntry(
        @Schema(description = "Path relative to the repository root", example = ".agents/rules/core.md")
        String path,

        @Schema(description = "SHA-256 of the content; null for a SYMLINK", nullable = true,
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        String sha256,

        @Schema(description = "FILE, EXECUTABLE or SYMLINK", example = "FILE")
        FileMode mode,

        @Schema(description = "Relative target of a SYMLINK; null otherwise", nullable = true, example = "../../.agents/skills/kazan")
        String target,

        @Schema(description = "Content size in bytes; null for a SYMLINK", nullable = true, example = "2048")
        Integer size,

        @Schema(description = "Whether Claude Code loads the file at every session start", example = "true")
        boolean alwaysOn,

        @Schema(description = "The pack or project that gives the path", example = "1d2e3f4a-5b6c-4d7e-8f9a-0b1c2d3e4f5a")
        UUID ownerId,

        @Schema(description = "The version of that pack or project the path comes from",
                example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
        UUID versionId,

        @Schema(description = "The inherited path a project file overrides; null for every other file", nullable = true,
                example = ".agents/rules/16-kagi-api-first.md")
        String overridesPath,

        @Schema(description = "The project whose override narrowed this inherited rule's paths, so sha256 and size are "
                + "those of the narrowed copy; null when not narrowed", nullable = true,
                example = "4d5e6f7a-8b9c-4d0e-1f2a-3b4c5d6e7f8a")
        UUID narrowedBy,

        @Schema(description = "The deny rules of a guardrail, merged by the CLI into permissions.deny of "
                + ".claude/settings.json; null for every other file and for a guardrail without deny rules", nullable = true,
                example = "[\"Read(./.env)\", \"Bash(git push --force:*)\"]")
        List<String> deny
) {
}
