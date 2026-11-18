package com.korur.oba.apps.kaya.script.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * One script with its index fields and the whole file as stored.
 */
@Schema(description = "A script of a version in full, with the file as stored")
public record ScriptDetailResponse(
        @Schema(description = "Script id", example = "3a7c1e2d-4b5f-4a6e-9d8c-7b6a5f4e3d2c")
        UUID id,

        @Schema(description = "Version the script belongs to", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID versionId,

        @Schema(description = "File name of the script", example = "check-links.sh")
        String name,

        @Schema(description = "Whether the script is delivered with the executable mode", example = "true")
        Boolean executable,

        @Schema(description = "Byte length of the file", example = "1024")
        Integer sizeBytes,

        @Schema(description = "The whole file as UTF-8 text", example = "#!/usr/bin/env bash\nset -euo pipefail\n")
        String content,

        @Schema(description = "When the script was stored", example = "2026-10-23T09:15:00Z")
        Instant createdAt,

        @Schema(description = "When the script was last changed", example = "2026-10-23T09:15:00Z")
        Instant updatedAt
) {
}
