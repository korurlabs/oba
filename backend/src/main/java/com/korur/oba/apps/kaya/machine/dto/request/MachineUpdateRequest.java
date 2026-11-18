package com.korur.oba.apps.kaya.machine.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.machine.constant.MachineConstant;
import com.korur.oba.apps.kaya.machine.enums.MachineStatus;

@Schema(description = "A status change of a machine; only RETIRED is accepted, and registering the "
        + "machine again with POST /machines makes it ACTIVE")
public record MachineUpdateRequest(
        @Schema(description = "New status", requiredMode = Schema.RequiredMode.REQUIRED, example = "RETIRED")
        @NotNull(message = MachineConstant.STATUS_REQUIRED_MESSAGE)
        MachineStatus status
) {

    /**
     * A machine becomes ACTIVE only together with a new token, which this request cannot return.
     */
    @Schema(hidden = true)
    @AssertTrue(message = MachineConstant.STATUS_RETIRED_ONLY_MESSAGE)
    public boolean isStatusRetired() {
        return status == null || status == MachineStatus.RETIRED;
    }
}
