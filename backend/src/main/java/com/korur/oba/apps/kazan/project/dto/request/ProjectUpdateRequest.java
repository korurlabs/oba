package com.korur.oba.apps.kazan.project.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Request payload used to update project metadata.
 */
@Schema(description = "Project update request")
public record ProjectUpdateRequest(

        // Updated project name if provided.
        @Schema(description = "Project name", nullable = true)
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String name,

        // Updated color if provided.
        @Schema(description = "Project color", nullable = true)
        @Size(max = 50, message = "Color must be at most 50 characters")
        String color
) {
}
