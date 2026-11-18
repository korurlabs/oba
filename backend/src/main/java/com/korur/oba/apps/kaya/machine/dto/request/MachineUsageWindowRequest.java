package com.korur.oba.apps.kaya.machine.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK
import java.math.BigDecimal;

// 4. Internal
import com.korur.oba.apps.kaya.machine.constant.MachineConstant;

/**
 * One usage window as the Claude Code status line reports it. The ranges are checked by the
 * service, which answers 400 KAY_VAL_009 for a value out of range.
 */
@Schema(description = "One usage limit window of a Claude subscription")
public record MachineUsageWindowRequest(
        @Schema(description = "Used share of the window, 0 to 100", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "23.5")
        @NotNull(message = MachineConstant.USAGE_PERCENTAGE_REQUIRED_MESSAGE)
        BigDecimal usedPercentage,

        @Schema(description = "When the window resets, in Unix epoch seconds; within 8 days of capturedAt",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "1792764000")
        @NotNull(message = MachineConstant.USAGE_RESETS_AT_REQUIRED_MESSAGE)
        Long resetsAt
) {
}
