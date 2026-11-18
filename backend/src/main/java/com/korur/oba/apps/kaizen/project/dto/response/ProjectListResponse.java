package com.korur.oba.apps.kaizen.project.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Response payload for listing projects.
 */
@Schema(description = "Project list response")
public record ProjectListResponse(

        // Unique project identifier.
        @Schema(description = "Project id", nullable = false)
        UUID id,

        // Project name shown in UI.
        @Schema(description = "Project name", nullable = false)
        String name,

        // Color used for project visual identification.
        @Schema(description = "Project color", nullable = true)
        String color,

        // Board that owns this project.
        @Schema(description = "Board id", nullable = false)
        UUID boardId,

        // Board name shown in UI.
        @Schema(description = "Board name", nullable = false)
        String boardName
) {
}
