package com.korur.oba.apps.kalkan.page.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * A new place for a page: a parent and a position among its children. A root page keeps no parent
 * and only changes its position; any other page needs a parent.
 */
@Schema(description = "A new parent and position for a page")
public record KalkanPageMoveRequest(
        @Schema(description = "New parent; empty only for a root page", nullable = true,
                example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID parentId,

        @NotNull(message = "Position is required")
        @PositiveOrZero(message = "Position must be zero or more")
        @Schema(description = "Order among the new siblings", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2")
        Integer position,

        @NotNull(message = "Version is required")
        @Schema(description = "The page version the move is based on", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "3")
        Long version
) {
}
