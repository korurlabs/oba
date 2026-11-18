package com.korur.oba.apps.kaya.plan.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

@Schema(description = "The content of one version of a plan")
public record PlanVersionContentResponse(
        @Schema(description = "Version number", example = "2")
        int number,

        @Schema(description = "The content as captured, at most 200000 characters")
        String content
) {
}
