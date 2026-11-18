package com.korur.oba.apps.kaya.release.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK

// 4. Internal

/**
 * Whether every repository of the organization composes a pack.
 */
@Schema(description = "Whether every repository of the organization composes the pack")
public record PackRequiredRequest(
        @Schema(description = "True to compose the pack into every repository, ahead of its own packs",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        @NotNull
        Boolean required
) {
}
