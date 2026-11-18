package com.korur.oba.apps.kaya.seed.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.seed.constant.SeedConstant;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedDrift;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedNotImported;

/**
 * A seed run: the packs of the agents repository, as the CLI read them from disk, the repositories
 * that use them, and what it left out and the drift it found.
 */
@Schema(description = "A seed run of today's repository files")
public record SeedRequest(
        @Schema(description = "Git commit of the agents clone", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "4f2a9c1e7b3d5a6f8e0c2b4d6f8a0c2e4b6d8f0a")
        @NotBlank
        @Size(max = SeedConstant.SOURCE_REF_MAX_LENGTH)
        String sourceRef,

        @Schema(description = "The packs to import", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty
        @Size(max = SeedConstant.SETS_MAX)
        List<@NotNull @Valid SeedSetRequest> sets,

        @Schema(description = "Repositories to connect, each with the full list of packs it uses; null changes no repository",
                nullable = true)
        @Size(max = SeedConstant.REPOSITORIES_MAX)
        List<@NotNull @Valid SeedRepositoryRequest> repositories,

        @Schema(description = "Paths the CLI did not import, with the reason", nullable = true)
        @Size(max = SeedConstant.REPORTED_PATHS_MAX)
        List<@NotNull @Valid SeedNotImported> notImported,

        @Schema(description = "Managed files whose disk copy differs from .agents/.managed.json", nullable = true)
        @Size(max = SeedConstant.REPORTED_PATHS_MAX)
        List<@NotNull @Valid SeedDrift> drift
) {
}
