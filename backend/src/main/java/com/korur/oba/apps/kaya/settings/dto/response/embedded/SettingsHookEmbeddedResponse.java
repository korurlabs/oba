package com.korur.oba.apps.kaya.settings.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

/**
 * A hook command registered in a settings file.
 */
@Schema(description = "A hook command registered in .claude/settings.json")
public record SettingsHookEmbeddedResponse(
        @Schema(description = "Hook event", example = "PreToolUse")
        String event,

        @Schema(description = "Tool matcher; null when the group has none", nullable = true, example = "Bash")
        String matcher,

        @Schema(description = "Hook type", nullable = true, example = "command")
        String type,

        @Schema(description = "The command", nullable = true, example = ".agents/hooks/dispatch.sh")
        String command
) {
}
