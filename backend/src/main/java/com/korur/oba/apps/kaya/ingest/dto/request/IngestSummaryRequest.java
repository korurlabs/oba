package com.korur.oba.apps.kaya.ingest.dto.request;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;

// 4. Internal

/**
 * The recorder's summary of the session. Its keys are the recorder's own snake_case index keys,
 * read as they are, plus the camelCase fields Kaya added. Text fields are cut to their columns
 * rather than refused, and an empty string reads as absent, because a refused batch would stop
 * the whole session from recording.
 */
@Schema(description = "The recorder's summary of the session")
public record IngestSummaryRequest(
        @Schema(description = "Title: the /rename title, else the first prompt", nullable = true)
        String title,

        @JsonProperty("session_title")
        @Schema(description = "The /rename title", nullable = true)
        String sessionTitle,

        @JsonProperty("first_prompt")
        @Schema(description = "The first prompt", nullable = true)
        String firstPrompt,

        @JsonProperty("started_at")
        @Schema(description = "When the session started", nullable = true)
        Instant startedAt,

        @JsonProperty("last_event_at")
        @Schema(description = "When the latest event was recorded", nullable = true)
        Instant lastEventAt,

        @Schema(description = "True once the session ended", nullable = true)
        Boolean ended,

        @Schema(description = "Working directory", nullable = true)
        String cwd,

        @Schema(description = "Branch checked out", nullable = true)
        String branch,

        @Schema(description = "Project directory name", nullable = true)
        String project,

        @Schema(description = "Kazan ticket key the session named", nullable = true, example = "KAYA-42")
        String ticket,

        @Schema(description = "Handoff feature directories the session wrote", nullable = true)
        List<String> features,

        @JsonProperty("briefed_features")
        @Schema(description = "Feature directories named in subagent briefs", nullable = true)
        List<String> briefedFeatures,

        @JsonProperty("event_count")
        @Schema(description = "Events the recorder holds for the session", nullable = true)
        Long eventCount,

        @Schema(description = "Model of the session", nullable = true, example = "claude-opus-5-5")
        String model,

        @Schema(description = "The git author email of the checkout", nullable = true, example = "ada@example.com")
        String gitAuthorEmail,

        @Schema(description = "The config release the session started on; absent while the CLI reports none", nullable = true)
        IngestConfigReleaseRequest configRelease
) {
}
