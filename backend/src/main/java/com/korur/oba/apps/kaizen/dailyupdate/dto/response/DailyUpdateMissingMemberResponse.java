package com.korur.oba.apps.kaizen.dailyupdate.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * An expected member without an update before the day's deadline: either no
 * update that day, or a first update at or after the deadline.
 */
@Schema(description = "Member without a daily update before the deadline")
public record DailyUpdateMissingMemberResponse(

        @Schema(description = "User id", example = "6f7a8b9c-0d1e-4f2a-9b3c-4d5e6f7a8b9c")
        UUID userId,

        @Schema(description = "Email address", example = "sam.taylor@example.com")
        String email,

        @Schema(description = "First name", nullable = true, example = "Sam")
        String firstName,

        @Schema(description = "Last name", nullable = true, example = "Taylor")
        String lastName,

        @Schema(description = "Time (UTC) of the member's first update of the day, posted after the deadline; null when not posted",
                nullable = true, example = "2026-10-19T08:02:00Z")
        Instant postedAt
) {
}
