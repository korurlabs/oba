package com.korur.oba.apps.kaya.settings.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.settings.dto.response.embedded.SettingsHookEmbeddedResponse;

/**
 * The settings file of a version with the permission rules and hooks parsed from it.
 */
@Schema(description = "The permission rules and hooks of a version's .claude/settings.json")
public record SettingsResponse(
        @Schema(description = "Settings id", example = "8f2e3d4c-5b6a-4f7e-8d9c-2b3c4d5e6f7a")
        UUID id,

        @Schema(description = "Version the settings belong to", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID versionId,

        @Schema(description = "Byte length of the file", example = "1024")
        Integer sizeBytes,

        @Schema(description = "permissions.allow rules", example = "[\"Bash(git status)\"]")
        List<String> allow,

        @Schema(description = "permissions.ask rules", example = "[\"Bash(git push:*)\"]")
        List<String> ask,

        @Schema(description = "permissions.deny rules", example = "[\"Read(./.env)\"]")
        List<String> deny,

        @Schema(description = "Registered hook commands, by event")
        List<SettingsHookEmbeddedResponse> hooks
) {
}
