package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.render.constant.RenderConstant;
import com.korur.oba.apps.kaya.render.enums.EntityKind;
import com.korur.oba.apps.kaya.render.enums.FileKind;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;

/**
 * Where a delivered path belongs, as {@link DeliveryPaths#classify} reads it.
 *
 * @param path         the delivered path, relative to the repository root
 * @param kind         the kind of the file
 * @param relativePath the path inside the kind's folder, or inside the skill directory for
 *                     {@link FileKind#SKILL}; null for an agents file, the settings and a link
 * @param skillName    the skill directory name for {@link FileKind#SKILL} and {@link FileKind#SKILL_LINK};
 *                     null otherwise
 * @param toolLink     the name of a {@link FileKind#TOOL_LINK}; null otherwise
 * @param placement    the placement of an {@link FileKind#AGENTS_FILE}; null otherwise
 */
public record DeliveryPath(
        String path,
        FileKind kind,
        String relativePath,
        String skillName,
        AgentsFilePlacement placement,
        ToolLinkName toolLink
) {

    /**
     * The entity the file is stored as. It is the {@link #kind()}, except that a file of a skill
     * directory is the {@link EntityKind#SKILL} for its SKILL.md and otherwise the part kind that
     * {@link SkillFolderParser#kindOf} gives its path and mode. Render, the save of a draft file and
     * the dependencies of a file all name a file's entity this way, so they cannot disagree.
     *
     * @param mode the mode of the file; {@link FileMode#EXECUTABLE} makes any skill part a script
     * @return the {@link EntityKind}, or null for a {@link FileKind#SKILL_LINK} or a {@link FileKind#TOOL_LINK},
     *         which is no entity
     */
    public EntityKind entityKind(FileMode mode) {
        if (kind == FileKind.SKILL_LINK || kind == FileKind.TOOL_LINK) {
            return null;
        }
        if (kind != FileKind.SKILL) {
            return EntityKind.valueOf(kind.name());
        }
        return relativePath.equals(RenderConstant.SKILL_FILE)
                ? EntityKind.SKILL
                : EntityKind.valueOf(SkillFolderParser.kindOf(relativePath, mode).name());
    }
}
