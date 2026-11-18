package com.korur.oba.apps.kaya.session.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

@Schema(description = "A machine that has sessions, as a wall filter option")
public record SessionFilterMachineResponse(
        @Schema(description = "Machine id")
        UUID id,

        @Schema(description = "Hostname of the machine", example = "ada-macbook")
        String machineName,

        @Schema(description = "User of the machine's sessions")
        UUID userId
) {
}
