package com.korur.oba.apps.kaizen.milestone.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.milestone.constant.MilestoneConstant;
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Request payload used to create a milestone on a board.
 */
@Schema(description = "Milestone create request")
public record MilestoneCreateRequest(

        // Not unique: two milestones of a board may share a name; the id identifies a milestone.
        @Schema(description = "Milestone name", requiredMode = Schema.RequiredMode.REQUIRED, example = "Public beta")
        @NotBlank(message = "Milestone name is required")
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String name,

        // Optional description of what the milestone delivers.
        @Schema(description = "Milestone description", nullable = true, example = "Store listings live and onboarding finished")
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        // Optional start date; must be on or before the target date.
        @Schema(description = "Start date (YYYY-MM-DD)", nullable = true, example = "2026-10-01")
        LocalDate startDate,

        // Date the milestone should be reached.
        @Schema(description = "Target date (YYYY-MM-DD)", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-12-15")
        @NotNull(message = "Target date is required")
        LocalDate targetDate,

        // Optional bar colour; the board's colour is used when omitted.
        @Schema(description = "Bar colour as #RRGGBB; defaults to the board colour", nullable = true, example = "#3B82F6")
        @Pattern(regexp = MilestoneConstant.COLOR_PATTERN, message = MilestoneConstant.COLOR_MESSAGE)
        String color,

        // Optional milestones the new one depends on; each must pass the add-dependency rules.
        @Size(max = 50, message = "Depends-on IDs must contain at most 50 entries")
        @Schema(description = "IDs of milestones this milestone depends on; duplicates are ignored", nullable = true,
                example = "[\"3f6c1d2e-8b7a-4c1e-9f0a-2b3c4d5e6f70\"]")
        List<@NotNull UUID> dependsOnIds
) {
}
