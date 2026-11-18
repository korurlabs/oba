package com.korur.oba.apps.kazan.dailyupdate.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.constant.DailyUpdateConstant;

/**
 * One ticket line of a daily update request.
 */
@Schema(description = "Daily update ticket line request")
public record DailyUpdateTicketRequest(

        // Ticket of the line; on create and for an added line it must be on a board the user is a member of.
        @Schema(description = "Ticket id", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "3f6c1d2e-8b7a-4c1e-9f0a-2b3c4d5e6f70")
        @NotNull(message = "Ticket id is required")
        UUID ticketId,

        // Progress the author reports for the ticket.
        @Schema(description = "Progress of the ticket, 0 to 100", requiredMode = Schema.RequiredMode.REQUIRED, example = "30")
        @NotNull(message = "Percentage is required")
        @Min(value = DailyUpdateConstant.MIN_PERCENTAGE, message = "Percentage must be at least 0")
        @Max(value = DailyUpdateConstant.MAX_PERCENTAGE, message = "Percentage must be at most 100")
        Integer percentage,

        // Optional note shown under the ticket line; blank is stored as null.
        @Schema(description = "Note under the ticket line", nullable = true, example = "Waiting for the final icons")
        @Size(max = DailyUpdateConstant.MAX_NOTE_LENGTH, message = "Note must be at most 500 characters")
        String note
) {
}
