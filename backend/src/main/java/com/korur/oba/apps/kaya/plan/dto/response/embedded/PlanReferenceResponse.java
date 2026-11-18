package com.korur.oba.apps.kaya.plan.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

@Schema(description = "A repository, pack, user or reviewer a plan names, with its display name")
public record PlanReferenceResponse(
        @Schema(description = "Id", example = "0b8e5f4a-6c2d-4b9e-8a1f-7d3c2b1a0e9f")
        UUID id,

        @Schema(description = "Display name; null when it is no longer known", nullable = true, example = "village")
        String name
) {
}
