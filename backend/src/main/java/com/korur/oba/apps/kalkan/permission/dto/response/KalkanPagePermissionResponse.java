package com.korur.oba.apps.kalkan.permission.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;

/**
 * A live grant of a root page with the display name of its subject.
 */
@Schema(description = "A live grant of a level on a root page")
public record KalkanPagePermissionResponse(
        @Schema(description = "Grant id", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID id,

        @Schema(description = "User id of the member holding the level", nullable = true,
                example = "3f6c2a1e-8b4d-4e7f-9a2c-5d1e0f3b7a64")
        UUID userId,

        @Schema(description = "Group id holding the level", nullable = true,
                example = "9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d")
        UUID groupId,

        @Schema(description = "Name of the member or group", example = "Jane Doe")
        String displayName,

        @Schema(description = "Level on the root and its subtree", example = "MANAGE")
        KalkanLevel level
) {
}
