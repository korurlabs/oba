package com.korur.oba.apps.kaya.session.dto.derived;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.apps.kaya.session.enums.SessionSkillUseSource;

/**
 * How often one skill was used in a session in one way, over every event of the session.
 *
 * @param skill       the skill or slash command name
 * @param source      how it was used
 * @param useCount    the number of uses, at least 1
 * @param firstUsedAt the first use
 */
public record DerivedSkillUse(
        String skill,
        SessionSkillUseSource source,
        int useCount,
        Instant firstUsedAt
) {
}
