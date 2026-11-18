package com.korur.oba.apps.kaizen.sprint.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceCreationMode;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceRunStatus;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceTicketAction;

/**
 * Response payload with a sprint series: its name, counter, "repeat
 * automatically" settings and the state of the job that rolls it over.
 */
@Schema(description = "Sprint series response")
public record SprintSeriesResponse(

        // Unique series identifier.
        @Schema(description = "Series id", example = "3f0a8c3e-6a51-4c0f-9d4e-2b1f7c9a5e11")
        UUID id,

        // Series name; its sprints are named "{name} {round}".
        @Schema(description = "Series name", example = "Atasprint")
        String name,

        // Round the next sprint of the series gets.
        @Schema(description = "Round of the next sprint", example = "2")
        int nextRound,

        // Name the next sprint of the series gets.
        @Schema(description = "Name of the next sprint", example = "Atasprint 2")
        String nextSprintName,

        // Whether the series repeats automatically.
        @Schema(description = "Repeat automatically", example = "true")
        boolean enabled,

        // Length of every sprint the job creates.
        @Schema(description = "Sprint length in days", example = "14")
        int lengthDays,

        // Where unfinished tickets go at the rollover.
        @Schema(description = "Where unfinished tickets go at the rollover", example = "MOVE_TO_NEXT_SPRINT")
        SprintRecurrenceTicketAction unfinishedTicketAction,

        // When the next sprint is created.
        @Schema(description = "When the next sprint is created", example = "DAYS_BEFORE")
        SprintRecurrenceCreationMode creationMode,

        // Days before the end date to create the next sprint.
        @Schema(description = "Days before the end date to create the next sprint", nullable = true, example = "2")
        Integer createDaysBefore,

        // Next time the job closes or starts a sprint of the series.
        @Schema(description = "End date of the ACTIVE sprint, else start date of the PLANNED head, when enabled; "
                + "else null", nullable = true, example = "2026-10-27T00:00:00Z")
        Instant nextRolloverAt,

        // Time the job creates the next sprint ahead.
        @Schema(description = "Time the next sprint is created ahead", nullable = true, example = "2026-10-25T00:00:00Z")
        Instant nextCreationAt,

        // Time of the last automatic run.
        @Schema(description = "Time of the last automatic run", nullable = true, example = "2026-10-20T00:00:00Z")
        Instant lastRunAt,

        // Outcome of the last automatic run.
        @Schema(description = "Outcome of the last automatic run", nullable = true, example = "OK")
        SprintRecurrenceRunStatus lastRunStatus,

        // Message of the last automatic run.
        @Schema(description = "Message of the last automatic run", nullable = true,
                example = "Another sprint is active on: Platform")
        String lastRunMessage,

        // Time zone of every rollover time.
        @Schema(description = "Time zone of every rollover time", example = "UTC")
        String timeZone
) {
}
