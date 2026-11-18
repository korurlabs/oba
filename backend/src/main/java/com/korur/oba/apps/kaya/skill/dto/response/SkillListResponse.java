package com.korur.oba.apps.kaya.skill.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * A row of the skill list: a skill's index fields and the size of its SKILL.md, without content or parts.
 */
@Schema(description = "A skill of a version with its index fields, without its content")
public record SkillListResponse(
        @Schema(description = "Skill id", example = "9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a")
        UUID id,

        @Schema(description = "Version the skill belongs to", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID versionId,

        @Schema(description = "Directory of the skill below .agents/skills/, which is its name",
                example = "logging-convention")
        String path,

        @Schema(description = "Name from the frontmatter", example = "logging-convention")
        String name,

        @Schema(description = "Description from the frontmatter", nullable = true,
                example = "Enforces LoggingService over @Slf4j")
        String description,

        @Schema(description = "Byte length of SKILL.md", example = "4096")
        Integer sizeBytes
) {
}
