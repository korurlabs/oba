package com.korur.oba.apps.kaya.machine.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.apps.kaya.machine.constant.MachineConstant;

/**
 * The latest usage limits the status line captured on a machine. Each window may be absent: the
 * status line has none before the first API response, or for an account without limits.
 */
@Schema(description = "The usage limits of the machine's Claude subscription, as last captured")
public record MachineUsageSnapshotRequest(
        @Schema(description = "When the status line captured the values, by the machine's clock",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-23T09:15:00Z")
        @NotNull(message = MachineConstant.USAGE_CAPTURED_AT_REQUIRED_MESSAGE)
        Instant capturedAt,

        @Schema(description = "The 5-hour window", nullable = true)
        @Valid
        MachineUsageWindowRequest fiveHour,

        @Schema(description = "The 7-day window", nullable = true)
        @Valid
        MachineUsageWindowRequest sevenDay
) {
}
