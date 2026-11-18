package com.korur.oba.apps.kalkan.page.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;

/**
 * The title and Markdown body of a page in one locale.
 */
@Schema(description = "A page's title and body in one locale")
public record KalkanPageTranslationRequest(
        @NotNull(message = "Locale is required")
        @Schema(description = "Content language", requiredMode = Schema.RequiredMode.REQUIRED, example = "TR")
        KalkanLocale locale,

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        @Schema(description = "Page title", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Bilgi Güvenliği Politikası")
        String title,

        @NotNull(message = "Body is required")
        @Size(max = 500000, message = "Body must be at most 500000 characters")
        @Schema(description = "Markdown body (GFM); may be empty", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "## Amaç\n\nBu politika ...")
        String body
) {
}
