package com.korur.oba.apps.kaya.render.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.render.enums.FileKind;
import com.korur.oba.apps.kaya.render.enums.ToolLoading;
import com.korur.oba.apps.kaya.render.enums.ToolPathState;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;

/**
 * One path an AI tool reads: when it loads it, the link it passes, the source it ends at and what the
 * scope delivers of it.
 */
@Schema(description = "One path an AI tool reads, the link on the way, the source it ends at and its state in the scope")
public record ToolPathResponse(
        @Schema(description = "Path the tool opens, from the repository root, with placeholders in angle brackets",
                example = ".claude/rules/<path>.md")
        String readPath,

        @Schema(description = "When the tool loads the path", example = "ALWAYS")
        ToolLoading loading,

        @Schema(description = "Link the tool passes on the way; null when it reads the source directly", example = ".claude/rules",
                nullable = true)
        String linkPath,

        @Schema(description = "Target of the link, relative to the link's directory; null when it reads the source directly",
                example = "../.agents/rules", nullable = true)
        String linkTarget,

        @Schema(description = "Path pattern of the source the read ends at, the pattern of its path mapping row",
                example = ".agents/rules/<path>.md")
        String sourcePattern,

        @Schema(description = "Kind of the source's path mapping row", example = "RULE")
        FileKind kind,

        @Schema(description = "Skill part of the source's row; null outside skill parts", example = "SCRIPT", nullable = true)
        SkillPartKind part,

        @Schema(description = "Agents file placement of the source's row; null for every other kind", example = "AGENTS_DIR",
                nullable = true)
        AgentsFilePlacement placement,

        @Schema(description = "What else decides the read; null when nothing", example = "a rule with paths in its frontmatter "
                + "loads when Claude Code reads a matching file", nullable = true)
        String note,

        @Schema(description = "Whether the scope gives the tool this path; null when no version id is sent", example = "LINKED",
                nullable = true)
        ToolPathState state,

        @Schema(description = "Files the tool finds there in the scope: the regular file's count when one is delivered at the "
                + "read path, the source row's count otherwise; null when no version id is sent", example = "12", nullable = true)
        Integer count
) {
}
