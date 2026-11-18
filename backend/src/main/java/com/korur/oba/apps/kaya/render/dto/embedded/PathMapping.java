package com.korur.oba.apps.kaya.render.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.render.enums.FileFormat;
import com.korur.oba.apps.kaya.render.enums.FileKind;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;

/**
 * Where one kind lives in a repository, as {@link com.korur.oba.apps.kaya.render.helper.DeliveryPaths#mappings}
 * lists it. The rows are built beside {@link com.korur.oba.apps.kaya.render.helper.DeliveryPaths#classify}
 * from the same folders and file names, so the page that shows them cannot drift from what Kaya delivers.
 *
 * @param kind        the kind a path of this row classifies to
 * @param part        the part of a skill directory for a {@link FileKind#SKILL} file other than its
 *                    SKILL.md; null otherwise
 * @param placement   the placement of an {@link FileKind#AGENTS_FILE}; null otherwise
 * @param link        the name of a {@link FileKind#TOOL_LINK}; null otherwise
 * @param pathPattern the path from the repository root, with a placeholder in angle brackets where
 *                    the skill name, the mount folder or the path inside the folder goes
 * @param mountedPattern the path a pack mounted in a folder stores the kind at, inside its namespace,
 *                    with the pack name at the pack placeholder; null when a mounted pack may not
 *                    deliver the kind
 * @param formats     the formats a file at the path holds
 * @param executable  whether a change at the path is an executable change, which an OWNER must approve
 * @param note        what else decides the row, such as the packs it applies to; null when nothing
 */
public record PathMapping(
        FileKind kind,
        SkillPartKind part,
        AgentsFilePlacement placement,
        ToolLinkName link,
        String pathPattern,
        String mountedPattern,
        List<FileFormat> formats,
        boolean executable,
        String note
) {
}
