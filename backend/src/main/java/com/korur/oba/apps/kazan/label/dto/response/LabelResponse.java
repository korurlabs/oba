package com.korur.oba.apps.kazan.label.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Response payload for a label.
 */
@Schema(description = "Label response")
public record LabelResponse(

        // Unique label identifier.
        @Schema(description = "Label id", nullable = false)
        UUID id,

        // Label name shown in UI.
        @Schema(description = "Label name", nullable = false)
        String name,

        // Color used for label visual identification.
        @Schema(description = "Label color", nullable = true)
        String color,

        // Board that owns this label.
        @Schema(description = "Board id", nullable = false)
        UUID boardId,

        // Board name shown in UI.
        @Schema(description = "Board name", nullable = false)
        String boardName
) {
}
