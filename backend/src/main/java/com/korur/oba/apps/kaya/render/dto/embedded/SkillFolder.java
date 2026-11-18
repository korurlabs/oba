package com.korur.oba.apps.kaya.render.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * A parsed skill directory: its SKILL.md and every other file as a {@link SkillPartFile}, as
 * {@link com.korur.oba.apps.kaya.render.helper.SkillFolderParser} returns it.
 *
 * @param name        the skill name, the directory name
 * @param skillFile   the exact bytes of SKILL.md
 * @param description the description of its frontmatter; null when it has none
 * @param parts       the other files, sorted by path
 */
public record SkillFolder(
        String name,
        byte[] skillFile,
        String description,
        List<SkillPartFile> parts
) {
}
