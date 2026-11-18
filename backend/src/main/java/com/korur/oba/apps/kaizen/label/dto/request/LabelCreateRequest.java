package com.korur.oba.apps.kaizen.label.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.milestone.constant.MilestoneConstant;
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Request payload used to create a new label.
 */
@Schema(description = "Label create request")
public record LabelCreateRequest(

        // User-provided label name for display and lookup.
        @Schema(description = "Label name", nullable = false)
        @NotBlank(message = "Label name is required")
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String name,

        // Optional color used for label visual identification.
        @Schema(description = "Label colour as #RRGGBB", nullable = true, example = "#3B82F6")
        @Pattern(regexp = MilestoneConstant.COLOR_PATTERN, message = MilestoneConstant.COLOR_MESSAGE)
        String color
) {
}
