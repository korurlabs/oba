package com.korur.oba.apps.kaizen.board.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * Response payload for listing boards.
 */
@Schema(description = "Board list response")
public record BoardListResponse(

        // Unique board identifier.
        @Schema(description = "Board id", nullable = false)
        UUID id,

        // Board creation timestamp.
        @Schema(description = "Created at", nullable = false)
        Instant createdAt,

        // Board update timestamp.
        @Schema(description = "Updated at", nullable = false)
        Instant updatedAt,

        // Board name shown in UI.
        @Schema(description = "Board name", nullable = false)
        String name,

        // Board description shown in UI.
        @Schema(description = "Board description", nullable = false)
        String description,

        // Short prefix used for ticket keys.
        @Schema(description = "Board prefix", nullable = false)
        String prefix,

        // URL slug derived from the board name, unique among live boards.
        @Schema(description = "URL slug of the board, derived from its name and unique among live boards",
                example = "fabrikam", nullable = false)
        String slug,

        // Color used for board visual identification.
        @Schema(description = "Board color", nullable = true)
        String color,

        // Icon identifier used for board visual identification.
        @Schema(description = "Board icon", nullable = true)
        String icon
) {
}
