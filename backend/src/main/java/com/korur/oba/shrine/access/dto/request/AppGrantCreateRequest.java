package com.korur.oba.shrine.access.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.enums.App;

/**
 * Request to grant an app to one user or to one group of the organization. Exactly one of userId and
 * groupId is set; the service refuses none or both with APP_VAL_001.
 */
@Schema(description = "App grant create request")
public record AppGrantCreateRequest(

        @Schema(description = "App to grant", requiredMode = Schema.RequiredMode.REQUIRED, example = "KAZAN")
        @NotNull(message = "App is required")
        App app,

        @Schema(description = "Member to grant the app to; null when groupId is set", nullable = true,
                example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID userId,

        @Schema(description = "Group to grant the app to; null when userId is set", nullable = true,
                example = "8d0f5c1e-3b7a-4f2e-9c61-5a4b2e7d9f10")
        UUID groupId
) {
}
