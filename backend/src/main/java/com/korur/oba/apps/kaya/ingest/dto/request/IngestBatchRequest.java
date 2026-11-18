package com.korur.oba.apps.kaya.ingest.dto.request;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaya.ingest.constant.IngestConstant;
import com.korur.oba.apps.kaya.machine.dto.request.MachineUsageSnapshotRequest;

/**
 * One batch of a recorder: new events of one session, its summary, the handoffs it wrote and the
 * latest usage limits. A batch needs events or usage. Events keep the recorder's snake_case keys
 * and are stored as sent; seq, at, event and agent_id are checked by the service.
 */
@Schema(description = "One recorder batch of one session")
public record IngestBatchRequest(
        @Schema(description = "Claude Code session id", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "3f1c2a9e-1b7d-4c55-9f0a-2d7e8c1b4a60")
        @NotBlank(message = IngestConstant.SESSION_ID_REQUIRED_MESSAGE)
        @Size(max = IngestConstant.SESSION_ID_MAX_LENGTH, message = IngestConstant.SESSION_ID_SIZE_MESSAGE)
        @Pattern(regexp = IngestConstant.SESSION_ID_PATTERN, message = IngestConstant.SESSION_ID_FORMAT_MESSAGE)
        String sessionId,

        @Schema(description = "The repository of the session", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = IngestConstant.REPOSITORY_REQUIRED_MESSAGE)
        @Valid
        IngestRepositoryRequest repository,

        // Older kaya recorders send this object under the key "agent"; the alias keeps them recording.
        @JsonAlias("agent")
        @Schema(description = "The sending machine", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = IngestConstant.MACHINE_REQUIRED_MESSAGE)
        @Valid
        IngestMachineRequest machine,

        @Schema(description = "The recorder's summary", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = IngestConstant.SUMMARY_REQUIRED_MESSAGE)
        @Valid
        IngestSummaryRequest summary,

        @Schema(description = "New events in seq order, 0 to 500; each holds seq (1 or more), at, event and the "
                + "recorder's other keys", nullable = true)
        @Size(max = IngestConstant.EVENTS_MAX, message = IngestConstant.EVENTS_SIZE_MESSAGE)
        List<Map<String, Object>> events,

        @Schema(description = "Handoff snapshots, 0 to 50", nullable = true)
        @Size(max = IngestConstant.HANDOFFS_MAX, message = IngestConstant.HANDOFFS_SIZE_MESSAGE)
        List<@Valid IngestHandoffRequest> handoffs,

        @Schema(description = "Plans the session captured, 0 to 20, in capture order", nullable = true)
        @Size(max = IngestConstant.PLANS_MAX, message = IngestConstant.PLANS_SIZE_MESSAGE)
        List<@Valid IngestPlanRequest> plans,

        @Schema(description = "The latest usage limits of the Claude subscription", nullable = true)
        @Valid
        MachineUsageSnapshotRequest usage
) {
}
