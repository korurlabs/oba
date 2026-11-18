package com.korur.oba.dojo.dto.request;

// 1. Framework-level
import jakarta.validation.constraints.NotBlank;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

@Schema(description = "Request to update an entity's status")
public record StatusUpdateRequest(

        @Schema(description = "Target status (e.g. ACTIVE, DISABLED)")
        @NotBlank
        String status
) {
}
