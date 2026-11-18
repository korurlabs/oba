package com.korur.oba.apps.kazan.dailyupdate.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal

/**
 * A nudge sent to a member who has not posted the day's update.
 */
@Schema(description = "Nudge sent to a member without today's daily update")
public record DailyUpdateNudgeResponse(

        @Schema(description = "User id of the nudged member", example = "6f7a8b9c-0d1e-4f2a-9b3c-4d5e6f7a8b9c")
        UUID recipientId,

        @Schema(description = "Business day (Europe/Istanbul) the nudge is for", example = "2026-11-06")
        LocalDate day,

        @Schema(description = "Time (UTC) the nudge was sent", example = "2026-11-06T07:12:00Z")
        Instant createdAt
) {
}
