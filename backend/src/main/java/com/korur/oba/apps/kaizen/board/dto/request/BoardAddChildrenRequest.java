package com.korur.oba.apps.kaizen.board.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * Request payload used to add existing boards as children of a parent board.
 */
@Schema(description = "Add child boards request")
public record BoardAddChildrenRequest(

        // List of board IDs to set as children of the target parent board.
        @Schema(description = "Child board IDs to add", nullable = false)
        @NotNull(message = "Child board IDs are required")
        @Size(min = 1, message = "At least one child board ID is required")
        List<@NotNull UUID> childBoardIds
) {
}
