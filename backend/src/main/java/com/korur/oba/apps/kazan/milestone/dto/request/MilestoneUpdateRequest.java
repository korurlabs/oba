package com.korur.oba.apps.kazan.milestone.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.time.LocalDate;

// 4. Internal
import com.korur.oba.apps.kazan.milestone.constant.MilestoneConstant;
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Request payload used to update a milestone. An omitted field is unchanged;
 * the clear flags set the nullable fields to null, because null alone means
 * "unchanged".
 */
@Schema(description = "Milestone update request")
public record MilestoneUpdateRequest(

        // New name if provided.
        @Schema(description = "Milestone name", nullable = true, example = "Public beta")
        @Size(min = 1, max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        @Pattern(regexp = ".*\\S.*", message = "Milestone name must not be blank")
        String name,

        // New description if provided.
        @Schema(description = "Milestone description", nullable = true, example = "Store listings live")
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        // New start date if provided.
        @Schema(description = "Start date (YYYY-MM-DD)", nullable = true, example = "2026-10-01")
        LocalDate startDate,

        // New target date if provided.
        @Schema(description = "Target date (YYYY-MM-DD)", nullable = true, example = "2026-12-15")
        LocalDate targetDate,

        // True sets the start date to null; wins over startDate.
        @Schema(description = "Set the start date to null", nullable = true, example = "false")
        Boolean clearStartDate,

        // True sets the description to null; wins over description.
        @Schema(description = "Set the description to null", nullable = true, example = "false")
        Boolean clearDescription,

        // New bar colour if provided.
        @Schema(description = "Bar colour as #RRGGBB", nullable = true, example = "#3B82F6")
        @Pattern(regexp = MilestoneConstant.COLOR_PATTERN, message = MilestoneConstant.COLOR_MESSAGE)
        String color
) {
}
