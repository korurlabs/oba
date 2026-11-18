package com.korur.oba.apps.kaya.machine.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

@Schema(description = "The live token of a machine, without its secret")
public record MachineTokenEmbeddedResponse(
        @Schema(description = "Token id")
        UUID id,

        @Schema(description = "First 12 characters of the token", example = "kyt_Q2xhdWRl")
        String tokenPrefix,

        @Schema(description = "When the token was issued")
        Instant createdAt,

        @Schema(description = "When a recorder last used the token", nullable = true)
        Instant lastUsedAt,

        @Schema(description = "When the token stops working")
        Instant expiresAt
) {
}
