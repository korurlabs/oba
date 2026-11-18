package com.korur.oba.apps.kaya.session.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;

// 4. Internal

@Schema(description = "The last captured content of a handoff file of a session")
public record SessionHandoffResponse(
        @Schema(description = "Path below handoffs/", example = "kaya/backend/session.md")
        String path,

        @Schema(description = "Value of the Status line: PARTIAL, DONE or BLOCKED; null when the file has none",
                nullable = true, example = "DONE")
        String status,

        @Schema(description = "Markdown content, at most 200000 characters")
        String content,

        @Schema(description = "When the content was captured")
        Instant capturedAt
) {
}
