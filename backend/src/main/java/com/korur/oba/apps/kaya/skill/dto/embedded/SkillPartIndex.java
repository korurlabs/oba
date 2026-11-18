package com.korur.oba.apps.kaya.skill.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;

/**
 * One link of a skill to a part the parse has already stored in the same version.
 *
 * @param kind   the kind of the part, which names the domain that holds it
 * @param partId the id the part's own service returned when it stored the part
 * @param path   the path of the file inside the skill directory
 */
public record SkillPartIndex(
        SkillPartKind kind,
        UUID partId,
        String path
) {
}
