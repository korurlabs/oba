package com.korur.oba.apps.kaya.version.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.version.enums.FileChange;

/**
 * How one rendered path differs between two versions, with a unified diff of its text when both
 * sides are text.
 */
@Schema(description = "How one path differs between a version and the version it is compared with")
public record FileDiffResponse(
        @Schema(description = "Path relative to the repository root", example = ".agents/rules/core.md")
        String path,

        @Schema(description = "ADDED, MODIFIED or DELETED", example = "MODIFIED")
        FileChange change,

        @Schema(description = "Mode before; null when ADDED", nullable = true, example = "FILE")
        FileMode oldMode,

        @Schema(description = "Mode after; null when DELETED", nullable = true, example = "FILE")
        FileMode newMode,

        @Schema(description = "SHA-256 of the content before; null when ADDED or for a SYMLINK", nullable = true,
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        String oldSha256,

        @Schema(description = "SHA-256 of the content after; null when DELETED or for a SYMLINK", nullable = true,
                example = "60303ae22b998861bce3b28f33eec1be758a213c86c93c076dbe9f558c11c752")
        String newSha256,

        @Schema(description = "Symlink target before; null unless it was a SYMLINK", nullable = true, example = "../.agents/skills/kazan")
        String oldSymlinkTarget,

        @Schema(description = "Symlink target after; null unless it is a SYMLINK", nullable = true, example = "../.agents/skills/kazan")
        String newSymlinkTarget,

        @Schema(description = "Whether either side holds bytes that are not UTF-8 text, so no line diff is given",
                example = "false")
        Boolean binary,

        @Schema(description = "Unified diff of the text with three lines of context; empty when only the mode changed, "
                + "null for binary content or a SYMLINK on either side", nullable = true,
                example = "--- a/.agents/rules/core.md\n+++ b/.agents/rules/core.md\n@@ -1,1 +1,1 @@\n-old\n+new\n")
        String unifiedDiff
) {
}
