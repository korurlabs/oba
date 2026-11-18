package com.korur.oba.apps.kaya.version.dto.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.version.enums.FileChange;

/**
 * One path that the draft and the owner's current version both changed, differently, since the
 * draft's base. It travels in the errorValues of a 409 KAY_CFG_002 under the key "conflicts".
 */
@Schema(description = "A path both the draft and the current version changed since the draft's base")
public record RebaseConflict(
        @Schema(description = "The path", example = ".agents/rules/core.md")
        String path,

        @Schema(description = "What the draft did to the path", example = "MODIFIED")
        FileChange draftChange,

        @Schema(description = "What the current version did to the path", example = "DELETED")
        FileChange currentChange
) {
}
