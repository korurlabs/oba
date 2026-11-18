package com.korur.oba.apps.kaya.signing.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;

// 4. Internal

/**
 * A public key release signatures verify against, as GET /signing-keys returns it.
 */
@Schema(description = "A public key Kaya signs releases with, or trusted during a key rotation")
public record SigningKeyResponse(
        @Schema(description = "Key id stored with each release it signed", example = "kaya-2026-10")
        String keyId,

        @Schema(description = "Raw 32-byte Ed25519 public key, base64", example = "11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo=")
        String publicKey,

        @Schema(description = "From when the key signs; null when not configured", nullable = true)
        Instant validFrom,

        @Schema(description = "Until when the key is trusted; null for the current signing key", nullable = true)
        Instant validTo
) {
}
