package com.korur.oba.apps.kaya.seed.dto.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.EntityKind;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;

/**
 * The report of a seed run, stored with the run. When a repository composition breaks a rule the
 * run discards every write and reports the violations, so a seed never releases a repository that
 * approval would refuse. Size limits are the exception: a seed mirrors today's files, so it imports
 * them and reports each broken limit as a warning.
 */
@Schema(description = "The report of a seed run")
public record SeedReport(
        @Schema(description = "Whether the run's sets, versions, packs and releases were kept; false when a composition "
                + "was refused and every write was discarded", example = "true")
        boolean applied,

        @Schema(description = "Imported entities per kind, one per file: a skill by its SKILL.md and each other file "
                + "of a skill directory as its part; refused files left out")
        Map<EntityKind, Integer> entities,

        @Schema(description = "One entry per set of the request, in request order")
        List<SeedSetReport> sets,

        @Schema(description = "The releases the run created; empty when nothing changed or nothing was applied")
        List<SeedReleaseReport> releases,

        @Schema(description = "Files the run refused; the rest of their sets was imported")
        List<SeedRefusedFile> refused,

        @Schema(description = "Rules the repository compositions break; non-empty only when applied is false")
        List<Violation> violations,

        @Schema(description = "Size limits the imported files break; they were imported anyway")
        List<SeedWarning> warnings,

        @Schema(description = "Paths not imported: as the CLI reported them, then each link the run left out because "
                + "it leads to or through a refused file")
        List<SeedNotImported> notImported,

        @Schema(description = "Managed files that drifted from .agents/.managed.json, as the CLI reported them")
        List<SeedDrift> drift
) {
}
