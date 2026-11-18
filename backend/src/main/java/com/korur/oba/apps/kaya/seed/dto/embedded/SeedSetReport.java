package com.korur.oba.apps.kaya.seed.dto.embedded;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.seed.enums.SeedSetKind;
import com.korur.oba.apps.kaya.seed.enums.SeedSetOutcome;

/**
 * What a seed run did to one pack of the request.
 */
@Schema(description = "The outcome of a seed run for one pack")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeedSetReport(
        @Schema(description = "The pack; null when the run created it and then discarded its writes",
                nullable = true, example = "1b2c3d4e-5f60-4718-9a0b-1c2d3e4f5a6b")
        UUID packId,

        @Schema(description = "PACK", example = "PACK")
        SeedSetKind kind,

        @Schema(description = "Name of the pack as stored", example = "village")
        String name,

        @Schema(description = "CREATED, UPDATED or UNCHANGED; what the run did or, when it discarded its writes, would have done",
                example = "UPDATED")
        SeedSetOutcome outcome,

        @Schema(description = "The SEED version the run created; null when unchanged or discarded", nullable = true,
                example = "5e6f7a8b-9c0d-4e1f-2a3b-4c5d6e7f8a9b")
        UUID versionId,

        @Schema(description = "Number of that version", nullable = true, example = "2")
        Integer versionNumber,

        @Schema(description = "Files of the pack after the run, refused files left out", example = "143")
        int fileCount,

        @Schema(description = "Files of the set the run refused", example = "1")
        int refusedCount,

        @Schema(description = "Whether the new version changes something that runs on a developer's machine", example = "false")
        boolean executableChange
) {
}
