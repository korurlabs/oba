package com.korur.oba.apps.kaya.session.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;

// 4. Internal

@Schema(description = "Data of a session-updated stream event: the session to read again")
public record SessionUpdatedStreamResponse(
        @Schema(description = "Claude Code session id")
        String sessionId,

        @Schema(description = "The session's last event after the change; null when the session was deleted", nullable = true)
        Instant lastEventAt
) {
}
