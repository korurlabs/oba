package com.korur.oba.shrine.access.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.shrine.access.constant.AccessConstant;

/**
 * Request to create a custom group in an organization. The name is unique in the organization, whatever
 * its case.
 */
@Schema(description = "Organization group create request")
public record OrganizationGroupCreateRequest(

        @Schema(description = "Group name, unique in the organization whatever its case",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "Engineering")
        @NotBlank(message = "Group name is required")
        @Size(max = AccessConstant.GROUP_NAME_MAX_LENGTH, message = "Group name must be at most 100 characters")
        String name,

        @Schema(description = "Group description", nullable = true, example = "Developers and testers")
        @NullOrNotBlank(message = "Description must be null or not blank")
        @Size(max = AccessConstant.GROUP_DESCRIPTION_MAX_LENGTH,
                message = "Description must be at most 500 characters")
        String description
) {
}
