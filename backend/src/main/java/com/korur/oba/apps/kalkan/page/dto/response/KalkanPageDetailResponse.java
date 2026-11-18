package com.korur.oba.apps.kalkan.page.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.dto.response.embedded.KalkanPageTranslationEmbeddedResponse;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;

/**
 * A page with its translations and the caller's level on its root.
 */
@Schema(description = "A page with its translations")
public record KalkanPageDetailResponse(
        @Schema(description = "Page id", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID id,

        @Schema(description = "Parent page; empty for a root page", nullable = true,
                example = "9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d")
        UUID parentId,

        @Schema(description = "Root of the page's tree; empty for a root page", nullable = true,
                example = "1b2c3d4e-5f6a-4b7c-8d9e-0f1a2b3c4d5e")
        UUID rootId,

        @Schema(description = "URL segment", example = "A.5.1")
        String slug,

        @Schema(description = "Slugs from the root, joined by a slash", example = "isms/kontroller/A.5.1")
        String path,

        @Schema(description = "Whether the page is a compliance document", example = "true")
        boolean compliance,

        @Schema(description = "Version to send with the next change", example = "3")
        long version,

        @Schema(description = "The caller's level on the page's root", example = "EDIT")
        KalkanLevel level,

        @Schema(description = "Live translations, by locale")
        List<KalkanPageTranslationEmbeddedResponse> translations,

        @Schema(description = "Last change of the page", example = "2026-11-05T10:15:30Z")
        Instant updatedAt
) {
}
