package com.korur.oba.apps.kaya.machine.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineAccountEmbeddedResponse;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineTokenEmbeddedResponse;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineUsageResponse;
import com.korur.oba.apps.kaya.machine.enums.MachineStatus;

@Schema(description = "A machine: one Claude Code installation on one computer of a user")
public record MachineResponse(
        @Schema(description = "Machine id")
        UUID id,

        @Schema(description = "User that owns the machine")
        UUID userId,

        @Schema(description = "Display name of the user", example = "Ada Lovelace")
        String userDisplayName,

        @Schema(description = "Hostname of the machine", example = "ada-macbook")
        String machineName,

        @Schema(description = "Claude Code version last reported; null shows \"Not reported\"", nullable = true, example = "2.3.1")
        String claudeCodeVersion,

        @Schema(description = "Account last reported; null shows \"Not reported\"", nullable = true)
        MachineAccountEmbeddedResponse account,

        @Schema(description = "Status", example = "ACTIVE")
        MachineStatus status,

        @Schema(description = "When a recorder of this machine was last seen", nullable = true)
        Instant lastSeenAt,

        @Schema(description = "The live token; null when the machine has none", nullable = true)
        MachineTokenEmbeddedResponse token,

        @Schema(description = "Usage limits of the machine's Claude subscription")
        MachineUsageResponse usage,

        @Schema(description = "Sessions the machine recorded", example = "42")
        long sessionCount,

        @Schema(description = "When the machine was registered")
        Instant createdAt,

        @Schema(description = "When the machine last changed")
        Instant updatedAt
) {
}
