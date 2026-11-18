package com.korur.oba.apps.kaya.machine.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.math.BigDecimal;
import java.time.Instant;

// 4. Internal

@Schema(description = "One usage limit window")
public record MachineUsageWindowResponse(
        @Schema(description = "Used share of the window, 0 to 100; null once the window has reset", nullable = true,
                example = "23.50")
        BigDecimal usedPercentage,

        @Schema(description = "When the window resets", example = "2026-10-23T14:30:00Z")
        Instant resetsAt,

        @Schema(description = "True when resetsAt has passed, so the stored percentage no longer holds", example = "false")
        boolean reset
) {
}
