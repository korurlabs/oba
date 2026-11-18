package com.korur.oba.apps.kaya.render.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.render.enums.FileFormat;
import com.korur.oba.apps.kaya.render.enums.FileKind;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;

/**
 * One row of the path mapping page: where a kind lives in a repository and how many files of it the
 * scope holds.
 */
@Schema(description = "Where one kind lives in a repository, with the number of its files in the scope")
public record PathMappingResponse(
        @Schema(description = "Kind a path of this row is delivered as", example = "SKILL")
        FileKind kind,

        @Schema(description = "Part of a skill directory; null outside skill parts", example = "SCRIPT", nullable = true)
        SkillPartKind part,

        @Schema(description = "Placement of an agents file; null for every other kind", example = "ROOT", nullable = true)
        AgentsFilePlacement placement,

        @Schema(description = "Name of a tool link; null for every other kind", example = "CLAUDE_RULES", nullable = true)
        ToolLinkName link,

        @Schema(description = "Path from the repository root, with placeholders in angle brackets",
                example = ".agents/skills/<name>/scripts/<path>")
        String pathPattern,

        @Schema(description = "Path a pack mounted in a folder stores the kind at, with placeholders in angle brackets; "
                + "null when a mounted pack may not deliver the kind", example = ".agents/skills/<pack>-<name>/scripts/<path>",
                nullable = true)
        String mountedPattern,

        @Schema(description = "Formats a file at the path holds", example = "[\"ANY\"]")
        List<FileFormat> formats,

        @Schema(description = "Whether a change at the path is an executable change, which an OWNER must approve", example = "true")
        boolean executable,

        @Schema(description = "What else decides the row; null when nothing", example = "also any file of the skill with the "
                + "executable bit", nullable = true)
        String note,

        @Schema(description = "Files of the row in the scope's versions; null when no version id is sent", example = "3",
                nullable = true)
        Integer count
) {
}
