package com.korur.oba.apps.kaya.seed.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedReport;
import com.korur.oba.apps.kaya.seed.enums.SeedRunStatus;

/**
 * A stored seed run with its outcome and report.
 */
@Schema(description = "A stored seed run with its report")
public record SeedRunResponse(
        @Schema(description = "The run", example = "7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d")
        UUID id,

        @Schema(description = "Git commit of the agents clone", example = "4f2a9c1e7b3d5a6f8e0c2b4d6f8a0c2e4b6d8f0a")
        String sourceRef,

        @Schema(description = "PASSED when the run was applied, every reached repository released and no drift "
                + "reported, refused files and warnings notwithstanding; FAILED otherwise", example = "PASSED")
        SeedRunStatus status,

        @Schema(description = "The report")
        SeedReport report,

        @Schema(description = "When the run was stored", example = "2026-10-23T09:15:00Z")
        Instant createdAt
) {
}
