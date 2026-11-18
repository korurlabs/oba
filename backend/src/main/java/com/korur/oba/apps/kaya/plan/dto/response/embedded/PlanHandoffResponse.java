package com.korur.oba.apps.kaya.plan.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

@Schema(description = "The newest snapshot of one handoff of the plan's feature")
public record PlanHandoffResponse(
        @Schema(description = "Path below handoffs/", example = "kaya-plans/backend/member-api.md")
        String path,

        @Schema(description = "Value of the Status line: PARTIAL, DONE or BLOCKED; null when the file has none", nullable = true, example = "DONE")
        String status,

        @Schema(description = "The unit of the spec the handoff implements: the first \"unit N\" of its Scope given section", nullable = true, example = "4")
        Integer unitNumber,

        @Schema(description = "Markdown content")
        String content
) {
}
