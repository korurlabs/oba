package com.korur.oba.apps.kaya.ingest.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.ingest.constant.IngestConstant;

@Schema(description = "The machine that sends the batch")
public record IngestMachineRequest(
        @Schema(description = "Hostname of the machine; must be the machine name of the token",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "ada-macbook")
        @NotBlank(message = IngestConstant.MACHINE_NAME_REQUIRED_MESSAGE)
        @Size(max = IngestConstant.MACHINE_NAME_MAX_LENGTH, message = IngestConstant.MACHINE_NAME_SIZE_MESSAGE)
        String machineName,

        @Schema(description = "Claude Code version", nullable = true, example = "2.3.1")
        String claudeCodeVersion,

        @Schema(description = "Claude account; null when Claude Code runs on an API key", nullable = true)
        @Valid
        IngestAccountRequest account
) {
}
