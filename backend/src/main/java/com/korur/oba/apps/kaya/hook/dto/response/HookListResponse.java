package com.korur.oba.apps.kaya.hook.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * A row of the hook list: a hook's index fields and size, without its content.
 */
@Schema(description = "A hook of a version with its index fields, without its content")
public record HookListResponse(
        @Schema(description = "Hook id", example = "8f2e3d4c-5b6a-4f7e-8d9c-2b3c4d5e6f7a")
        UUID id,

        @Schema(description = "Version the hook belongs to", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID versionId,

        @Schema(description = "Path below .agents/hooks/", example = "dispatch.sh")
        String path,

        @Schema(description = "Whether the script is delivered with the executable mode", example = "true")
        Boolean executable,

        @Schema(description = "Byte length of the file", example = "4096")
        Integer sizeBytes
) {
}
