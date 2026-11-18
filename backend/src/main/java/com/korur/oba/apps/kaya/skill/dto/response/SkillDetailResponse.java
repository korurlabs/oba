package com.korur.oba.apps.kaya.skill.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.skill.dto.response.embedded.SkillPartEmbeddedResponse;

/**
 * One skill with its index fields, its whole SKILL.md as stored, and the part files it links.
 */
@Schema(description = "A skill of a version in full, with SKILL.md as stored and its parts")
public record SkillDetailResponse(
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
        Integer sizeBytes,

        @Schema(description = "The whole SKILL.md as UTF-8 text, frontmatter included",
                example = "---\nname: logging-convention\n---\n# Logging\n")
        String content,

        @Schema(description = "The other files of the skill directory, ordered by path")
        List<SkillPartEmbeddedResponse> parts,

        @Schema(description = "When the skill was stored", example = "2026-10-23T09:15:00Z")
        Instant createdAt,

        @Schema(description = "When the skill was last changed", example = "2026-10-23T09:15:00Z")
        Instant updatedAt
) {
}
