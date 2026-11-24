package com.korur.oba.apps.kalkan.page.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * A live page among the most recently changed ones.
 */
@Schema(description = "A recently changed page")
public record KalkanPageRecentResponse(
        @Schema(description = "Page id", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID id,

        @Schema(description = "Title in the requested locale, else in the first locale of the page", nullable = true,
                example = "Information Security Policy")
        String title,

        @Schema(description = "Slugs from the root, joined by a slash", example = "policies/information-security")
        String path,

        @Schema(description = "Slug of the page's root, which names its section", example = "policies")
        String rootSlug,

        @Schema(description = "Last change of the page", example = "2026-11-05T10:15:30Z")
        Instant updatedAt,

        @Schema(description = "Name of the member who changed it last", nullable = true, example = "Jane Doe")
        String updatedByName
) {
}
