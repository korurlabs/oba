package com.korur.oba.apps.kaya.machine.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

@Schema(description = "A registered machine and its recorder token, which is shown only in this response")
public record MachineCreatedResponse(
        @Schema(description = "The machine")
        MachineResponse machine,

        @Schema(description = "The recorder token in plaintext; Kaya keeps only its hash", example = "kyt_Q2xhdWRlQ29kZVJlY29yZGVyVG9rZW5FeGFtcGxlMDA")
        String token
) {
}
