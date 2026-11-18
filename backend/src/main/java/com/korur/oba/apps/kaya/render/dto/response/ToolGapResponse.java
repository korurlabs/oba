package com.korur.oba.apps.kaya.render.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

/**
 * A path an AI tool reads that Kaya does not deliver.
 */
@Schema(description = "A path an AI tool reads that Kaya does not deliver")
public record ToolGapResponse(
        @Schema(description = "Path from the repository root, or from the user's home when it starts with ~", example = ".cursor/rules/")
        String path,

        @Schema(description = "What the tool reads there and why Kaya leaves it out", example = "Cursor's own rule files")
        String note
) {
}
