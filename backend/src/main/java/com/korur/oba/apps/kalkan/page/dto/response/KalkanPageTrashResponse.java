package com.korur.oba.apps.kalkan.page.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * The top page of one delete, which a restore brings back with the pages the delete took.
 */
@Schema(description = "A deleted page the caller can restore")
public record KalkanPageTrashResponse(
        @Schema(description = "Page id, to pass to the restore", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID id,

        @Schema(description = "Title in the requested locale, else in the first locale the page had", nullable = true,
                example = "Information Security Policy")
        String title,

        @Schema(description = "Slugs from the root, joined by a slash, as the page had them", example = "isms/policies")
        String path,

        @Schema(description = "When the page was deleted", example = "2026-11-05T10:15:30Z")
        Instant deletedAt,

        @Schema(description = "Name of the member who deleted it", nullable = true, example = "Jane Doe")
        String deletedByName,

        @Schema(description = "Number of pages below it that the same delete took", example = "4")
        long descendantCount
) {
}
