package com.korur.oba.apps.kaizen.organizationsetting.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Request payload that sets or clears the security board of an organization.
 */
@Schema(description = "Organization security board update request")
public record OrganizationSecurityBoardUpdateRequest(

        // Board that receives the organization's security tickets; null clears it.
        @Schema(description = "Board id; null clears the security board", nullable = true,
                example = "4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a")
        UUID boardId
) {
}
