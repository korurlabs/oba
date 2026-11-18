package com.korur.oba.apps.kaya.rule.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.rule.dto.response.embedded.RuleConventionEmbeddedResponse;

/**
 * A row of the rule list: a rule's index fields and size, without its content.
 */
@Schema(description = "A rule of a version with its index fields, without its content")
public record RuleListResponse(
        @Schema(description = "Rule id", example = "7e1d2c3b-4a5f-4e6d-9c8b-1a2b3c4d5e6f")
        UUID id,

        @Schema(description = "Version the rule belongs to", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID versionId,

        @Schema(description = "Path below .agents/rules/", example = "11-backend-rules.md")
        String path,

        @Schema(description = "Description from the frontmatter", nullable = true,
                example = "Rules for the Spring Boot backends")
        String description,

        @Schema(description = "Globs of the files the rule applies to; empty for an always-on rule",
                example = "[\"projects/kaya/backend/**\"]")
        List<String> paths,

        @Schema(description = "Whether the rule loads in every session", example = "false")
        Boolean alwaysOn,

        @Schema(description = "Path of the pack rule this rule narrows", nullable = true,
                example = "11-backend-rules.md")
        String overridesPath,

        @Schema(description = "Conventions the body links to, by path")
        List<RuleConventionEmbeddedResponse> conventions,

        @Schema(description = "Byte length of the file", example = "2048")
        Integer sizeBytes
) {
}
