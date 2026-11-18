package com.korur.oba.apps.kazan.label.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.milestone.constant.MilestoneConstant;
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Request payload used to update label metadata.
 */
@Schema(description = "Label update request")
public record LabelUpdateRequest(

        // Updated label name if provided.
        @Schema(description = "Label name", nullable = true)
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String name,

        // Updated color if provided.
        @Schema(description = "Label colour as #RRGGBB", nullable = true, example = "#3B82F6")
        @Pattern(regexp = MilestoneConstant.COLOR_PATTERN, message = MilestoneConstant.COLOR_MESSAGE)
        String color
) {
}
