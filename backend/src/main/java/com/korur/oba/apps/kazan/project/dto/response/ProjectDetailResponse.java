package com.korur.oba.apps.kazan.project.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * Response payload for a detailed project view.
 */
@Schema(description = "Project detail response")
public record ProjectDetailResponse(

        // Unique project identifier.
        @Schema(description = "Project id", nullable = false)
        UUID id,

        // Project creation timestamp.
        @Schema(description = "Created at", nullable = false)
        Instant createdAt,

        // Project update timestamp.
        @Schema(description = "Updated at", nullable = false)
        Instant updatedAt,

        // Project name shown in UI.
        @Schema(description = "Project name", nullable = false)
        String name,

        // Color used for project visual identification.
        @Schema(description = "Project color", nullable = true)
        String color,

        // Board identifier this project belongs to.
        @Schema(description = "Board id", nullable = false)
        UUID boardId
) {
}
