package com.korur.oba.apps.kaya.machine.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.machine.constant.MachineConstant;

@Schema(description = "A machine to register")
public record MachineCreateRequest(
        @Schema(description = "Display label of the machine, such as its host name; unique among the user's machines. "
                + "The machine token, not this name, identifies the machine when it sends batches",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "ada-macbook")
        @NotBlank(message = MachineConstant.MACHINE_NAME_REQUIRED_MESSAGE)
        @Size(max = MachineConstant.MACHINE_NAME_MAX_LENGTH, message = MachineConstant.MACHINE_NAME_SIZE_MESSAGE)
        String machineName,

        @Schema(description = "User that owns the machine; the caller when null. "
                + "Another user needs ADMIN", nullable = true)
        UUID userId
) {
}
