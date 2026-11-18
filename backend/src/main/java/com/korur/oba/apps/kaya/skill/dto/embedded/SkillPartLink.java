package com.korur.oba.apps.kaya.skill.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;

/**
 * One link of a skill to one of its parts: which skill, which part, and where the part lies in the
 * skill's directory. A part two skills share has one link in each.
 *
 * @param skillId   the skill
 * @param skillName the skill's directory name below .agents/skills/
 * @param versionId the version of the skill
 * @param kind      the kind of the part
 * @param partId    the part row of that kind
 * @param path      the part's path relative to the skill directory
 */
public record SkillPartLink(
        UUID skillId,
        String skillName,
        UUID versionId,
        SkillPartKind kind,
        UUID partId,
        String path
) {
}
