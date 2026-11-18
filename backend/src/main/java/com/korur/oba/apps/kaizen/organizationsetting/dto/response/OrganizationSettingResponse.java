package com.korur.oba.apps.kaizen.organizationsetting.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * Response payload for the Kaizen settings of an organization.
 */
@Schema(description = "Organization settings response")
public record OrganizationSettingResponse(

        // Platform organization the settings belong to.
        @Schema(description = "Organization id", example = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b")
        UUID organizationId,

        // Board that receives the organization's security tickets.
        @Schema(description = "Security board id", nullable = true, example = "4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a")
        UUID securityBoardId,

        // Last change of the settings.
        @Schema(description = "Last update timestamp", example = "2026-10-31T09:30:00Z")
        Instant updatedAt
) {
}
