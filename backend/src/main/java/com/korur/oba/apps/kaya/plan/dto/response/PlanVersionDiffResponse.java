package com.korur.oba.apps.kaya.plan.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

@Schema(description = "The changes of one version of a plan against the version before")
public record PlanVersionDiffResponse(
        @Schema(description = "Version number", example = "2")
        int number,

        @Schema(description = "Unified diff against version number - 1; empty for version 1 or an unchanged text")
        String diff
) {
}
