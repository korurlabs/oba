package com.korur.oba.apps.kaya.session.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.apps.kaya.session.enums.SessionSkillUseSource;

@Schema(description = "How often a session used a skill, per way of use")
public record SessionSkillUseResponse(
        @Schema(description = "Skill name", example = "api-integration")
        String skill,

        @Schema(description = "TOOL, COMMAND or PRELOADED", example = "TOOL")
        SessionSkillUseSource source,

        @Schema(description = "Number of uses", example = "2")
        int useCount,

        @Schema(description = "First use")
        Instant firstUsedAt
) {
}
