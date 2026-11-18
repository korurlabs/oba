package com.korur.oba.shrine.organization.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Request payload used to update organization metadata.
 * This is a full update: every field is written to the organization, and a null optional field clears it.
 */
@Schema(description = "Organization update request. Full update: every field is applied, and a null optional field clears the stored value.")
public record OrganizationUpdateRequest(

        // Organization name. Required on every update.
        @Schema(description = "Organization name", requiredMode = Schema.RequiredMode.REQUIRED, example = "Sensey B.V.")
        @NotBlank(message = "Organization name is required")
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String name,

        // Optional description. Null clears it.
        @Schema(description = "Organization description. Null clears the stored value. An empty or whitespace-only string is rejected.", nullable = true, example = "Software consultancy based in Amsterdam")
        @NullOrNotBlank(message = "Description must be null or not blank")
        @Size(max = 512, message = "Description must be at most 512 characters")
        String description,

        // Optional industry classification. Null clears it.
        @Schema(description = "Industry. Null clears the stored value. An empty or whitespace-only string is rejected.", nullable = true, example = "Technology")
        @NullOrNotBlank(message = "Industry must be null or not blank")
        @Size(max = 100, message = "Industry must be at most 100 characters")
        String industry,

        // Optional company size descriptor. Null clears it.
        @Schema(description = "Company size. Null clears the stored value. An empty or whitespace-only string is rejected.", nullable = true, example = "11-50")
        @NullOrNotBlank(message = "Company size must be null or not blank")
        @Size(max = 50, message = "Company size must be at most 50 characters")
        String companySize,

        // Optional ISO 3166-1 alpha-3 country code. Null clears it.
        @Schema(description = "Country code (ISO 3166-1 alpha-3). Null clears the stored value. An empty or whitespace-only string is rejected.", nullable = true, example = "NLD")
        @NullOrNotBlank(message = "Country code must be null or not blank")
        @Size(max = 3, message = "Country code must be at most 3 characters")
        String country,

        // Optional company website URL. Null clears it.
        @Schema(description = "Website URL. Null clears the stored value. An empty or whitespace-only string is rejected.", nullable = true, example = "https://sensey.io")
        @NullOrNotBlank(message = "Website must be null or not blank")
        @Size(max = 512, message = "Website must be at most 512 characters")
        String website
) {
}
