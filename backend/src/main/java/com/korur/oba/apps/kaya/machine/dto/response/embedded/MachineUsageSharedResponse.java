package com.korur.oba.apps.kaya.machine.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

@Schema(description = "Another active machine on the same account")
public record MachineUsageSharedResponse(
        @Schema(description = "Machine id")
        UUID machineId,

        @Schema(description = "Display label of the machine", example = "ada-desktop")
        String machineName
) {
}
