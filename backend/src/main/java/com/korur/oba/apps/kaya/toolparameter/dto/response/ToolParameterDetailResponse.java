package com.korur.oba.apps.kaya.toolparameter.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.toolparameter.enums.ToolParameterFormat;

/**
 * One tool parameter file with its index fields and the whole file as stored.
 */
@Schema(description = "A tool parameter file of a version in full, with the file as stored")
public record ToolParameterDetailResponse(
        @Schema(description = "Tool parameter file id", example = "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d")
        UUID id,

        @Schema(description = "Version the file belongs to", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID versionId,

        @Schema(description = "File name", example = "openai.yml")
        String name,

        @Schema(description = "JSON, YAML or TEXT, from the file extension", example = "YAML")
        ToolParameterFormat format,

        @Schema(description = "Byte length of the file", example = "512")
        Integer sizeBytes,

        @Schema(description = "The whole file as UTF-8 text", example = "interface:\n  display_name: Kaizen\n")
        String content,

        @Schema(description = "When the file was stored", example = "2026-10-23T09:15:00Z")
        Instant createdAt,

        @Schema(description = "When the file was last changed", example = "2026-10-23T09:15:00Z")
        Instant updatedAt
) {
}
