package com.korur.oba.apps.kaya.guardrail.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.guardrail.enums.GuardrailCategory;
import com.korur.oba.apps.kaya.guardrail.enums.GuardrailEnforcement;

/**
 * A row of the guardrail list: a guardrail's index fields and size, without its content.
 */
@Schema(description = "A guardrail of a version with its index fields, without its content")
public record GuardrailListResponse(
        @Schema(description = "Guardrail id", example = "9a3f4e5d-6c7b-4a8e-9f0d-3c4d5e6f7a8b")
        UUID id,

        @Schema(description = "Version the guardrail belongs to", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID versionId,

        @Schema(description = "Path below .agents/rules/guardrails/", example = "protect-secrets.md")
        String path,

        @Schema(description = "File name without .md", example = "protect-secrets")
        String name,

        @Schema(description = "Category from the frontmatter", example = "SECURITY")
        GuardrailCategory category,

        @Schema(description = "Description from the frontmatter", nullable = true,
                example = "Agents never read or print secrets.")
        String description,

        @Schema(description = "Permission rules the delivered settings deny; empty for an instruction only",
                example = "[\"Read(./.env)\", \"Bash(cat .env:*)\"]")
        List<String> deny,

        @Schema(description = "BLOCK when the guardrail has deny rules, INSTRUCT otherwise", example = "BLOCK")
        GuardrailEnforcement enforcement,

        @Schema(description = "Byte length of the file", example = "512")
        Integer sizeBytes
) {
}
