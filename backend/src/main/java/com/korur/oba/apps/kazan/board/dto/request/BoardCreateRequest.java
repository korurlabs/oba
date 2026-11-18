package com.korur.oba.apps.kazan.board.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.milestone.constant.MilestoneConstant;
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Request payload used to create a new board.
 */
@Schema(description = "Board create request")
public record BoardCreateRequest(

        // User-provided board name for display and lookup.
        @Schema(description = "Board name", nullable = false)
        @NotBlank(message = "Board name is required")
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String name,

        // Optional description used for UI context and documentation.
        @Schema(description = "Board description", nullable = true)
        @Size(max = 512, message = "Description must be at most 512 characters")
        String description,

        // Short prefix used for ticket keys (e.g., "PROJ" in "PROJ-123").
        @Schema(description = "Board prefix for ticket keys", nullable = false)
        @NotBlank(message = "Board prefix is required")
        @Size(max = 10, message = "Prefix must be at most 10 characters")
        String prefix,

        // Optional color used for board visual identification.
        @Schema(description = "Board colour as #RRGGBB", nullable = true, example = "#3B82F6")
        @Pattern(regexp = MilestoneConstant.COLOR_PATTERN, message = MilestoneConstant.COLOR_MESSAGE)
        String color,

        // Optional icon identifier used for board visual identification.
        @Schema(description = "Board icon", nullable = true)
        @Size(max = 100, message = "Icon must be at most 100 characters")
        String icon,

        // Optional parent board identifier for hierarchical boards.
        @Schema(description = "Parent board id", nullable = true)
        UUID parentBoardId,

        // Optional list of members to add on creation (owner is always added automatically).
        @Schema(description = "Initial board members", nullable = true)
        List<BoardMemberAddRequest> members
) {
}
