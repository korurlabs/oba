package com.korur.oba.apps.kazan.board.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.milestone.constant.MilestoneConstant;
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Request payload used to update board metadata.
 */
@Schema(description = "Board update request")
public record BoardUpdateRequest(

        // Updated board name if provided.
        @Schema(description = "Board name", nullable = true)
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String name,

        // Updated description if provided.
        @Schema(description = "Board description", nullable = true)
        @Size(max = 512, message = "Description must be at most 512 characters")
        String description,

        // Updated prefix if provided.
        @Schema(description = "Board prefix", nullable = true)
        @Size(max = 10, message = "Prefix must be at most 10 characters")
        String prefix,

        // Updated color if provided.
        @Schema(description = "Board colour as #RRGGBB", nullable = true, example = "#3B82F6")
        @Pattern(regexp = MilestoneConstant.COLOR_PATTERN, message = MilestoneConstant.COLOR_MESSAGE)
        String color,

        // Updated icon if provided.
        @Schema(description = "Board icon", nullable = true)
        @Size(max = 100, message = "Icon must be at most 100 characters")
        String icon,

        // New parent board if provided. A null parentBoardId alone keeps the parent because partial updates omit it.
        @Schema(description = "Parent board id; null keeps the current parent", nullable = true)
        UUID parentBoardId,

        // True makes the board a top-level board; wins over parentBoardId.
        @Schema(description = "Detach the parent board; wins over parentBoardId", nullable = true, example = "false")
        Boolean clearParent
) {
}
