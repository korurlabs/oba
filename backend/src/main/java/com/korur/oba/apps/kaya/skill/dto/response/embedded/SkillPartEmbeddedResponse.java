package com.korur.oba.apps.kaya.skill.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;

/**
 * A file of a skill directory other than SKILL.md: its kind, the part it is, and its path in the skill.
 */
@Schema(description = "A part file a skill links, with the number of skills that share it")
public record SkillPartEmbeddedResponse(
        @Schema(description = "Kind of the part; the detail endpoint of that kind reads it", example = "SCRIPT")
        SkillPartKind kind,

        @Schema(description = "Id of the part in its own kind", example = "3b9a0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d")
        UUID id,

        @Schema(description = "Path of the file inside the skill directory", example = "scripts/build.sh")
        String path,

        @Schema(description = "How many skills of the version link this part, this one included", example = "2")
        Integer usedBySkills
) {
}
