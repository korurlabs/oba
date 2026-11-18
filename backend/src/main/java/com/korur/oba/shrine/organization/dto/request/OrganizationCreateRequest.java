package com.korur.oba.shrine.organization.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Request payload used to create a new organization.
 */
@Schema(description = "Organization create request")
public record OrganizationCreateRequest(

        // User-provided organization name for display and lookup.
        @Schema(description = "Organization name", nullable = false)
        @NotBlank(message = "Organization name is required")
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String name,

        // Optional description used for UI context and documentation.
        @Schema(description = "Organization description", nullable = true)
        @Size(max = 512, message = "Description must be at most 512 characters")
        String description,

        // Optional industry classification.
        @Schema(description = "Industry", nullable = true)
        @Size(max = 100, message = "Industry must be at most 100 characters")
        String industry,

        // Optional company size descriptor.
        @Schema(description = "Company size", nullable = true)
        @Size(max = 50, message = "Company size must be at most 50 characters")
        String companySize,

        // Optional ISO 3166-1 alpha-3 country code.
        @Schema(description = "Country code (ISO 3166-1 alpha-3)", nullable = true)
        @Size(max = 3, message = "Country code must be at most 3 characters")
        String country,

        // Optional company website URL.
        @Schema(description = "Website URL", nullable = true)
        @Size(max = 512, message = "Website must be at most 512 characters")
        String website
) {
}
