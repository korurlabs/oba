package com.korur.oba.apps.kaizen.sprint.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.sprint.constant.SprintRecurrenceConstant;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceCreationMode;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceTicketAction;

/**
 * Request payload with the full "repeat automatically" settings of a sprint series.
 *
 * It carries settings only: the series name comes from the sprint name, and
 * the first round from the create dialog (1) or the sprint's trailing number.
 * The date and days-before rules depend on the sprint's dates, so the service
 * checks them, and only when enabled is true; switching off always succeeds.
 */
@Schema(description = "Sprint series request")
public record SprintSeriesRequest(

        // Whether the series creates and rolls over its sprints.
        @Schema(description = "Repeat automatically", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        @NotNull(message = "Enabled is required")
        Boolean enabled,

        // Length of every sprint the job creates.
        @Schema(description = "Sprint length in days, 1 to 365", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "14")
        @NotNull(message = "Length is required")
        @Min(value = SprintRecurrenceConstant.MIN_LENGTH_DAYS, message = "Length must be at least 1 day")
        @Max(value = SprintRecurrenceConstant.MAX_LENGTH_DAYS, message = "Length must be at most 365 days")
        Integer lengthDays,

        // Where unfinished tickets go at the rollover.
        @Schema(description = "Where unfinished tickets go at the rollover", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "MOVE_TO_NEXT_SPRINT")
        @NotNull(message = "Unfinished ticket action is required")
        SprintRecurrenceTicketAction unfinishedTicketAction,

        // When the next sprint is created.
        @Schema(description = "When the next sprint is created", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "AT_END")
        @NotNull(message = "Creation mode is required")
        SprintRecurrenceCreationMode creationMode,

        // Days before the end date to create the next sprint; used only with DAYS_BEFORE.
        @Schema(description = "Days before the end date to create the next sprint; required with DAYS_BEFORE, "
                + "1 to the length minus 1", nullable = true, example = "2")
        Integer createDaysBefore
) {
}
