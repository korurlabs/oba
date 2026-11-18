package com.korur.oba.apps.kaizen.dailyupdate.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * The members of one organization who have not posted a daily update by the
 * deadline of one day, Europe/Istanbul time.
 */
@Schema(description = "Members without a daily update before the deadline of one day")
public record DailyUpdateMissingResponse(

        @Schema(description = "Organization id", example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID organizationId,

        @Schema(description = "Reported day in Europe/Istanbul (YYYY-MM-DD)", example = "2026-10-19")
        LocalDate date,

        @Schema(description = "Deadline of the day: 10:30 Europe/Istanbul, as a UTC instant", example = "2026-10-19T07:30:00Z")
        Instant deadline,

        @Schema(description = "False on a Saturday or Sunday, when no update is expected", example = "true")
        boolean workday,

        @Schema(description = "True when the deadline has passed at the time of the request", example = "true")
        boolean deadlinePassed,

        @Schema(description = "Number of members expected to post that day; 0 on a weekend", example = "12")
        int expectedCount,

        @Schema(description = "Expected members without an update before the deadline: those who have not posted, "
                + "by first name, last name and email, then those who posted late, by posting time")
        List<DailyUpdateMissingMemberResponse> members
) {
}
