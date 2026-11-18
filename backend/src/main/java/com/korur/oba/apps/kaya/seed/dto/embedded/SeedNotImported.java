package com.korur.oba.apps.kaya.seed.dto.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.seed.constant.SeedConstant;

/**
 * A path under .agents/, .claude/ or the agents repository that the CLI left out of the seed on
 * purpose, with its reason. The server cannot see these paths, so it keeps them as reported.
 */
@Schema(description = "A path the CLI did not import, with the reason")
public record SeedNotImported(
        @Schema(description = "Path relative to its repository", requiredMode = Schema.RequiredMode.REQUIRED,
                example = ".agents/memory/lessons.md")
        @NotBlank
        @Size(max = SeedConstant.REPORTED_PATH_MAX_LENGTH)
        String path,

        @Schema(description = "Why it was not imported", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Lessons stay in git")
        @NotBlank
        @Size(max = SeedConstant.REASON_MAX_LENGTH)
        String reason
) {
}
