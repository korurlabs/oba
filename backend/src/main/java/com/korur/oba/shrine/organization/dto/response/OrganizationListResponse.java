package com.korur.oba.shrine.organization.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

/**
 * Response payload for listing organizations.
 */
@Schema(description = "Organization list response")
public record OrganizationListResponse(

        // Unique organization identifier.
        @Schema(description = "Organization id", nullable = false)
        UUID id,

        // Organization creation timestamp.
        @Schema(description = "Created at", nullable = false)
        Instant createdAt,

        // Organization name shown in UI.
        @Schema(description = "Organization name", nullable = false)
        String name,

        // Current organization status.
        @Schema(description = "Organization status", nullable = false)
        OrganizationStatus status
) {
}
