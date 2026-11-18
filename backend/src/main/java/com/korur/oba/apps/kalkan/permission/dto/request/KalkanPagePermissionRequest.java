package com.korur.oba.apps.kalkan.permission.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;

/**
 * One grant of a root page's permission list; the PUT body is a list of these and replaces every
 * live grant. A subject is a member's user id; group subjects come with organization groups.
 */
@Schema(description = "A grant of a level on a root page to one member")
public record KalkanPagePermissionRequest(
        @Schema(description = "User id of an active member of the organization", nullable = true,
                example = "3f6c2a1e-8b4d-4e7f-9a2c-5d1e0f3b7a64")
        UUID userId,

        @Schema(description = "Group id; not accepted until organization groups exist", nullable = true,
                example = "9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d")
        UUID groupId,

        @NotNull(message = "Level is required")
        @Schema(description = "Level on the root and its subtree", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "EDIT")
        KalkanLevel level
) {
}
