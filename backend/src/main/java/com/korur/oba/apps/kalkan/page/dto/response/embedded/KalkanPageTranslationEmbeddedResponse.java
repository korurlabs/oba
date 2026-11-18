package com.korur.oba.apps.kalkan.page.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;

/**
 * A page's live translation in one locale, with who changed it last.
 */
@Schema(description = "A page's title and body in one locale")
public record KalkanPageTranslationEmbeddedResponse(
        @Schema(description = "Content language", example = "EN")
        KalkanLocale locale,

        @Schema(description = "Page title", example = "Information Security Policy")
        String title,

        @Schema(description = "Markdown body (GFM)", example = "## Purpose\n\nThis policy ...")
        String body,

        @Schema(description = "Last change of this translation", example = "2026-11-05T10:15:30Z")
        Instant updatedAt,

        @Schema(description = "Name of the member who changed it last", nullable = true, example = "Jane Doe")
        String updatedByName
) {
}
