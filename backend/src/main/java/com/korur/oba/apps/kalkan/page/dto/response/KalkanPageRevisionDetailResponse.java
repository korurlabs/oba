package com.korur.oba.apps.kalkan.page.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;

/**
 * One entry of a page's history with the body the write gave.
 */
@Schema(description = "A revision of a page's translation with its body")
public record KalkanPageRevisionDetailResponse(
        @Schema(description = "Revision id", example = "7d8e9f0a-1b2c-4d3e-8f4a-5b6c7d8e9f0a")
        UUID id,

        @Schema(description = "Content language of the revised translation", example = "EN")
        KalkanLocale locale,

        @Schema(description = "Title the write gave", example = "Information Security Policy")
        String title,

        @Schema(description = "Page version the write produced", example = "3")
        long version,

        @Schema(description = "When the write happened", example = "2026-11-05T10:15:30Z")
        Instant createdAt,

        @Schema(description = "Name of the member who wrote it", nullable = true, example = "Jane Doe")
        String createdByName,

        @Schema(description = "Markdown body (GFM) the write gave", example = "## Purpose\n\nThis policy ...")
        String body
) {
}
