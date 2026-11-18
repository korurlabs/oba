package com.korur.oba.apps.kaya.machine.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.machine.enums.MachineUsageState;

@Schema(description = "The usage limits of a machine's Claude subscription, from the account's newest snapshot")
public record MachineUsageResponse(
        @Schema(description = "KNOWN, or why the limits are unknown", example = "KNOWN")
        MachineUsageState state,

        @Schema(description = "When the snapshot was captured; null unless KNOWN", nullable = true)
        Instant capturedAt,

        @Schema(description = "The machine that reported the snapshot; null unless KNOWN", nullable = true)
        UUID reportedByMachineId,

        @Schema(description = "Hostname of the machine that reported the snapshot; null unless KNOWN", nullable = true,
                example = "ada-macbook")
        String reportedByMachineName,

        @Schema(description = "The other active machines whose latest account is the same")
        List<MachineUsageSharedResponse> sharedWith,

        @Schema(description = "The 5-hour window; null when the snapshot has none", nullable = true)
        MachineUsageWindowResponse fiveHour,

        @Schema(description = "The 7-day window; null when the snapshot has none", nullable = true)
        MachineUsageWindowResponse sevenDay
) {
}
