package com.korur.oba.apps.kaya.machine.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

@Schema(description = "A new recorder token, which is shown only in this response")
public record MachineTokenCreatedResponse(
        @Schema(description = "The recorder token in plaintext; Kaya keeps only its hash", example = "kyt_Q2xhdWRlQ29kZVJlY29yZGVyVG9rZW5FeGFtcGxlMDA")
        String token,

        @Schema(description = "Token id")
        UUID tokenId,

        @Schema(description = "First 12 characters of the token", example = "kyt_Q2xhdWRl")
        String tokenPrefix,

        @Schema(description = "When the token stops working")
        Instant expiresAt
) {
}
