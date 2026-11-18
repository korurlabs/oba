package com.korur.oba.apps.kaya.skill.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * The index of one skill directory as the render domain extracts it on parse: the stored SKILL.md, its
 * frontmatter fields, and the links to the parts stored from the other files of the directory.
 *
 * @param name          the frontmatter name, which is also the directory below .agents/skills/
 * @param contentBlobId the blob holding the whole SKILL.md, frontmatter included
 * @param description   the frontmatter description, or null
 * @param parts         the {@link SkillPartIndex} links; may be empty
 */
public record SkillIndex(
        String name,
        UUID contentBlobId,
        String description,
        List<SkillPartIndex> parts
) {
}
