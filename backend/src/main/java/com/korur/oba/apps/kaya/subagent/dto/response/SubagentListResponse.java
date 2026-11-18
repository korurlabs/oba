package com.korur.oba.apps.kaya.subagent.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * A row of the subagent list: a subagent's index fields and size, without its content.
 */
@Schema(description = "A subagent of a version with its index fields, without its content")
public record SubagentListResponse(
        @Schema(description = "Subagent id", example = "8f2e3d4c-5b6a-4f7e-8d9c-2b3c4d5e6f7a")
        UUID id,

        @Schema(description = "Version the subagent belongs to", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID versionId,

        @Schema(description = "Path below .claude/agents/", example = "coding-engineer.md")
        String path,

        @Schema(description = "Name from the frontmatter, unique within the version", example = "coding-engineer")
        String name,

        @Schema(description = "Description from the frontmatter", nullable = true,
                example = "Use for every implementation task")
        String description,

        @Schema(description = "Model from the frontmatter", nullable = true, example = "opus")
        String model,

        @Schema(description = "Turn limit from the frontmatter, at least 1", nullable = true, example = "50")
        Integer maxTurns,

        @Schema(description = "Tools from the frontmatter; null when it names none, so the subagent "
                + "inherits the session's tools", nullable = true, example = "[\"Read\", \"Edit\", \"Bash\"]")
        List<String> tools,

        @Schema(description = "Byte length of the file", example = "4096")
        Integer sizeBytes
) {
}
