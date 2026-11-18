package com.korur.oba.apps.kazan.dailyupdate.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.constant.DailyUpdateConstant;

/**
 * Request payload used to preview a new update or an edit. The ticket lines
 * follow the create and edit rules, but the focus may be empty, so the author
 * sees the card while still composing it; the create and edit themselves
 * require a focus.
 */
@Schema(description = "Daily update preview request")
public record DailyUpdatePreviewRequest(

        // Ticket lines in the order they are shown; a ticket may appear once.
        @Schema(description = "Ticket lines in display order", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "At least one ticket is required")
        @Size(max = DailyUpdateConstant.MAX_TICKETS, message = "An update can hold at most 30 tickets")
        List<@Valid @NotNull DailyUpdateTicketRequest> tickets,

        // Optional while composing; blank previews as null.
        @Schema(description = "What the author will focus on today", nullable = true, example = "Finish the mobile menu design")
        @Size(max = DailyUpdateConstant.MAX_TEXT_LENGTH, message = "Focus must be at most 2000 characters")
        String focus,

        // Optional questions to colleagues; blank is stored as null.
        @Schema(description = "Questions to colleagues", nullable = true, example = "Who reviews the icon set?")
        @Size(max = DailyUpdateConstant.MAX_TEXT_LENGTH, message = "Questions must be at most 2000 characters")
        String questions
) {
}
