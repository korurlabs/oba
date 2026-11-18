package com.korur.oba.apps.kaya.version.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.FileEncoding;
import com.korur.oba.apps.kaya.render.enums.FileMode;

/**
 * One rendered file of a version with its content, sent as text when it is valid UTF-8 and as
 * base64 otherwise.
 */
@Schema(description = "One file of a version with its content")
public record VersionFileContentResponse(
        @Schema(description = "Version the file belongs to", example = "6b0e2f4a-1c3d-4e5f-8a9b-0c1d2e3f4a5b")
        UUID versionId,

        @Schema(description = "Path relative to the repository root", example = ".agents/rules/core.md")
        String path,

        @Schema(description = "FILE, EXECUTABLE or SYMLINK", example = "FILE")
        FileMode mode,

        @Schema(description = "SHA-256 of the content, lower-case hex; null for a SYMLINK", nullable = true,
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        String sha256,

        @Schema(description = "Content size in bytes; null for a SYMLINK", nullable = true, example = "2048")
        Integer size,

        @Schema(description = "Relative target of a SYMLINK; null otherwise", nullable = true, example = "../.agents/skills/kazan")
        String symlinkTarget,

        @Schema(description = "UTF_8 when content is the text itself, BASE64 when the bytes are not valid UTF-8; "
                + "null for a SYMLINK", nullable = true, example = "UTF_8")
        FileEncoding encoding,

        @Schema(description = "The content in the given encoding; null for a SYMLINK", nullable = true, example = "# Core rules")
        String content,

        @Schema(description = "Inherited path a project file overrides; null for every other file", nullable = true,
                example = ".agents/rules/16-kagi-api-first.md")
        String overridesPath
) {
}
