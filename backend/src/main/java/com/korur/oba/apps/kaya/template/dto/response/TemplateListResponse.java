package com.korur.oba.apps.kaya.template.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * A row of the template list: a template's index fields and size, without its content.
 */
@Schema(description = "A template of a version with its index fields, without its content")
public record TemplateListResponse(
        @Schema(description = "Template id", example = "8f2e3d4c-5b6a-4f7e-8d9c-2b3c4d5e6f7a")
        UUID id,

        @Schema(description = "Version the template belongs to", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID versionId,

        @Schema(description = "Path below .agents/templates/", example = "handoff.md")
        String path,

        @Schema(description = "Title from the frontmatter", nullable = true, example = "Handoff template")
        String title,

        @Schema(description = "Byte length of the file", example = "4096")
        Integer sizeBytes
) {
}
