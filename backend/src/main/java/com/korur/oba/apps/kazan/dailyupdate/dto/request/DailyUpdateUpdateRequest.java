package com.korur.oba.apps.kazan.dailyupdate.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.constant.DailyUpdateConstant;

/**
 * Request payload used to edit a daily update. It is the full new content, not a patch:
 * tickets lists the update's lines in order, so a line is added by listing a new
 * ticket and removed by leaving it out, and focus and questions are replaced.
 */
@Schema(description = "Daily update edit request")
public record DailyUpdateUpdateRequest(

        // Ticket lines in the order they are shown; a ticket may appear once.
        @Schema(description = "Ticket lines in display order", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "At least one ticket is required")
        @Size(max = DailyUpdateConstant.MAX_TICKETS, message = "An update can hold at most 30 tickets")
        List<@Valid @NotNull DailyUpdateTicketRequest> tickets,

        // Focus for today; required, stored stripped.
        @Schema(description = "What the author will focus on today", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Finish the mobile menu design")
        @NotBlank(message = "Focus is required")
        @Size(max = DailyUpdateConstant.MAX_TEXT_LENGTH, message = "Focus must be at most 2000 characters")
        String focus,

        // Optional questions to colleagues; blank is stored as null.
        @Schema(description = "Questions to colleagues", nullable = true, example = "Who reviews the icon set?")
        @Size(max = DailyUpdateConstant.MAX_TEXT_LENGTH, message = "Questions must be at most 2000 characters")
        String questions
) {
}
