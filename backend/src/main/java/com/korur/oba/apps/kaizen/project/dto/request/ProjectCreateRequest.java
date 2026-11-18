package com.korur.oba.apps.kaizen.project.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Request payload used to create a new project.
 */
@Schema(description = "Project create request")
public record ProjectCreateRequest(

        // User-provided project name for display and lookup.
        @Schema(description = "Project name", nullable = false)
        @NotBlank(message = "Project name is required")
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String name,

        // Optional color used for project visual identification.
        @Schema(description = "Project color", nullable = true)
        @Size(max = 50, message = "Color must be at most 50 characters")
        String color
) {
}
