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
 * Response payload for a detailed organization view.
 */
@Schema(description = "Organization detail response")
public record OrganizationDetailResponse(

        // Unique organization identifier.
        @Schema(description = "Organization id", nullable = false)
        UUID id,

        // Organization creation timestamp.
        @Schema(description = "Created at", nullable = false)
        Instant createdAt,

        // Organization update timestamp.
        @Schema(description = "Updated at", nullable = false)
        Instant updatedAt,

        // Organization name shown in UI.
        @Schema(description = "Organization name", nullable = false)
        String name,

        // Optional description text.
        @Schema(description = "Organization description", nullable = true)
        String description,

        // Industry classification.
        @Schema(description = "Industry", nullable = true)
        String industry,

        // Company size descriptor.
        @Schema(description = "Company size", nullable = true)
        String companySize,

        // ISO 3166-1 alpha-3 country code.
        @Schema(description = "Country code (ISO 3166-1 alpha-3)", nullable = true)
        String country,

        // Company website URL.
        @Schema(description = "Website URL", nullable = true)
        String website,

        // Current organization status.
        @Schema(description = "Organization status", nullable = false)
        OrganizationStatus status
) {
}
