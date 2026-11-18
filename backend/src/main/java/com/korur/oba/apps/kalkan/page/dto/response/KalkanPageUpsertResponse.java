package com.korur.oba.apps.kalkan.page.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.enums.KalkanUpsertOutcome;

/**
 * What an upsert by path did, or would do on a dry run.
 */
@Schema(description = "The outcome of an upsert by path")
public record KalkanPageUpsertResponse(
        @Schema(description = "Page id; empty when a dry run would create the page", nullable = true,
                example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID id,

        @Schema(description = "Slugs from the root, joined by a slash", example = "isms/kontroller/A.5.1")
        String path,

        @Schema(description = "What was done", example = "UPDATED")
        KalkanUpsertOutcome outcome,

        @Schema(description = "Page version after the write; on a dry run the version it has now, 0 for a new page",
                example = "4")
        long version
) {
}
